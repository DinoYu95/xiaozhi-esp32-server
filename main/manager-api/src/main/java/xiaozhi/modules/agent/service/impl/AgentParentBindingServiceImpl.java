package xiaozhi.modules.agent.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.agent.dto.AgentBindParentDTO;
import xiaozhi.modules.agent.dto.AgentDTO;
import xiaozhi.modules.agent.entity.AgentEntity;
import xiaozhi.modules.agent.dao.AgentDao;
import xiaozhi.modules.agent.service.AgentParentBindingService;
import xiaozhi.modules.agent.vo.ParentUserSearchVO;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.util.ParentDeviceAccessHelper;
import xiaozhi.modules.parent.util.ParentDeviceDisplayResolver;

@Service
@RequiredArgsConstructor
public class AgentParentBindingServiceImpl implements AgentParentBindingService {

    private final DeviceDao deviceDao;
    private final DeviceService deviceService;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final ParentUserDao parentUserDao;
    private final AgentDao agentDao;
    private final DeviceChildDao deviceChildDao;

    @Override
    public void enrichParentBinding(List<AgentDTO> agents) {
        if (agents == null || agents.isEmpty()) {
            return;
        }
        List<String> agentIds = agents.stream()
                .map(AgentDTO::getId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (agentIds.isEmpty()) {
            return;
        }

        List<DeviceEntity> devices = deviceDao.selectList(
                new LambdaQueryWrapper<DeviceEntity>().in(DeviceEntity::getAgentId, agentIds));
        Map<String, List<DeviceEntity>> agentDevices = new HashMap<>();
        Set<String> allDeviceKeys = new HashSet<>();
        for (DeviceEntity device : devices) {
            if (device == null || StringUtils.isBlank(device.getAgentId())) {
                continue;
            }
            agentDevices.computeIfAbsent(device.getAgentId(), k -> new ArrayList<>()).add(device);
            collectDeviceKeys(device.getId(), allDeviceKeys);
        }

        Map<String, ParentDeviceBindingEntity> ownerByDeviceKey = loadPrimaryOwners(allDeviceKeys);
        Map<String, Long> memberCountByDeviceKey = loadActiveMemberCounts(allDeviceKeys);
        Map<Long, String> nicknameCache = new HashMap<>();

        for (AgentDTO dto : agents) {
            dto.setParentActivated(false);
            dto.setBoundParentCount(0);
            List<DeviceEntity> agentDeviceList = agentDevices.getOrDefault(dto.getId(), List.of());
            DeviceEntity displayDevice = pickDisplayDevice(agentDeviceList, ownerByDeviceKey);
            if (displayDevice != null) {
                dto.setParentDeviceDisplayName(ParentDeviceDisplayResolver.resolveDeviceName(
                        deviceDao, deviceChildDao, parentDeviceBindingDao, displayDevice.getId()));
            }
            for (DeviceEntity device : agentDeviceList) {
                ParentDeviceBindingEntity owner = findOwnerForDevice(device.getId(), ownerByDeviceKey);
                if (owner != null) {
                    dto.setParentActivated(true);
                    dto.setOwnerParentId(owner.getParentUserId());
                    dto.setOwnerDeviceId(device.getId());
                    dto.setOwnerParentNickname(resolveNickname(owner.getParentUserId(), nicknameCache));
                    dto.setBoundParentCount(
                            Math.toIntExact(findMemberCount(device.getId(), memberCountByDeviceKey)));
                    break;
                }
            }
        }
    }

    @Override
    public List<AgentDTO> filterByActivation(List<AgentDTO> agents, String activationFilter) {
        if (agents == null || agents.isEmpty()) {
            return List.of();
        }
        if (StringUtils.isBlank(activationFilter) || ACTIVATION_ALL.equalsIgnoreCase(activationFilter.trim())) {
            return agents;
        }
        boolean wantActive = ACTIVATION_ACTIVE.equalsIgnoreCase(activationFilter.trim());
        return agents.stream()
                .filter(a -> wantActive == Boolean.TRUE.equals(a.getParentActivated()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ParentUserSearchVO> searchParentUsers(String keyword) {
        LambdaQueryWrapper<ParentUserEntity> wrapper = new LambdaQueryWrapper<ParentUserEntity>()
                .orderByDesc(ParentUserEntity::getUpdateTime)
                .last("LIMIT 20");
        if (StringUtils.isNotBlank(keyword)) {
            String kw = keyword.trim();
            Long parentId = parseLongOrNull(kw);
            if (parentId != null) {
                wrapper.and(w -> w.like(ParentUserEntity::getNickname, kw).or().eq(ParentUserEntity::getId, parentId));
            } else {
                wrapper.like(ParentUserEntity::getNickname, kw);
            }
        }
        List<ParentUserEntity> list = parentUserDao.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(u -> {
            ParentUserSearchVO vo = new ParentUserSearchVO();
            vo.setId(u.getId());
            vo.setNickname(StringUtils.isNotBlank(u.getNickname()) ? u.getNickname() : ("家长" + u.getId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminBindParent(Long scopeUserId, String agentId, AgentBindParentDTO dto) {
        if (dto == null || dto.getParentUserId() == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        if (!checkAgentOwnedBy(scopeUserId, agentId)) {
            throw new RenException(ErrorCode.AGENT_NOT_FOUND);
        }
        ParentUserEntity parentUser = parentUserDao.selectById(dto.getParentUserId());
        if (parentUser == null) {
            throw new RenException("家长用户不存在");
        }

        List<DeviceEntity> devices = deviceService.getUserDevices(scopeUserId, agentId);
        if (devices == null || devices.isEmpty()) {
            throw new RenException("该智能体下暂无设备，无法绑定家长");
        }
        DeviceEntity device = resolveTargetDevice(devices, dto.getDeviceId());
        String deviceId = device.getId();
        boolean replaceExisting = Boolean.TRUE.equals(dto.getReplaceExisting());

        ParentDeviceBindingEntity currentOwner =
                ParentDeviceAccessHelper.findPrimaryOwner(parentDeviceBindingDao, deviceId);
        if (currentOwner != null && !replaceExisting) {
            throw new RenException("该设备已有绑定家长，如需更换请使用更新家长");
        }
        if (currentOwner != null
                && currentOwner.getParentUserId().equals(dto.getParentUserId())) {
            throw new RenException("该家长已是当前绑定家长");
        }

        Date now = new Date();
        if (currentOwner != null) {
            currentOwner.setStatus(ParentDeviceBindingEntity.STATUS_REMOVED);
            currentOwner.setUpdatedAt(now);
            parentDeviceBindingDao.updateById(currentOwner);
        }

        ParentDeviceBindingEntity existingActive = ParentDeviceAccessHelper.findActiveBinding(
                parentDeviceBindingDao, dto.getParentUserId(), deviceId);
        if (existingActive != null) {
            existingActive.setRole(ParentDeviceBindingEntity.ROLE_OWNER);
            existingActive.setIsPrimary(1);
            existingActive.setInvitedBy(null);
            existingActive.setStatus(ParentDeviceBindingEntity.STATUS_ACTIVE);
            existingActive.setBindTime(now);
            existingActive.setBindSource(replaceExisting ? "admin_replace" : "admin");
            existingActive.setUpdatedAt(now);
            parentDeviceBindingDao.updateById(existingActive);
            return;
        }

        ParentDeviceBindingEntity anyBinding = ParentDeviceAccessHelper.findAnyBinding(
                parentDeviceBindingDao, dto.getParentUserId(), deviceId);
        if (anyBinding != null) {
            anyBinding.setRole(ParentDeviceBindingEntity.ROLE_OWNER);
            anyBinding.setIsPrimary(1);
            anyBinding.setInvitedBy(null);
            anyBinding.setStatus(ParentDeviceBindingEntity.STATUS_ACTIVE);
            anyBinding.setBindTime(now);
            anyBinding.setBindSource(replaceExisting ? "admin_replace" : "admin");
            anyBinding.setUpdatedAt(now);
            parentDeviceBindingDao.updateById(anyBinding);
        } else {
            ParentDeviceBindingEntity binding = new ParentDeviceBindingEntity();
            binding.setParentUserId(dto.getParentUserId());
            binding.setDeviceId(deviceId);
            binding.setBindTime(now);
            binding.setBindSource(replaceExisting ? "admin_replace" : "admin");
            binding.setRole(ParentDeviceBindingEntity.ROLE_OWNER);
            binding.setIsPrimary(1);
            binding.setStatus(ParentDeviceBindingEntity.STATUS_ACTIVE);
            binding.setCreateTime(now);
            binding.setUpdatedAt(now);
            parentDeviceBindingDao.insert(binding);
        }
    }

    private static DeviceEntity resolveTargetDevice(List<DeviceEntity> devices, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return devices.get(0);
        }
        String normalized = ParentDeviceAccessHelper.normalizeDeviceId(deviceId);
        for (DeviceEntity device : devices) {
            if (device.getId().equals(deviceId)
                    || ParentDeviceAccessHelper.normalizeDeviceId(device.getId()).equals(normalized)) {
                return device;
            }
        }
        throw new RenException("指定设备不属于该智能体");
    }

    private Map<String, ParentDeviceBindingEntity> loadPrimaryOwners(Set<String> deviceKeys) {
        if (deviceKeys.isEmpty()) {
            return Map.of();
        }
        List<ParentDeviceBindingEntity> owners = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .eq(ParentDeviceBindingEntity::getRole, ParentDeviceBindingEntity.ROLE_OWNER)
                        .eq(ParentDeviceBindingEntity::getIsPrimary, 1));
        Map<String, ParentDeviceBindingEntity> map = new HashMap<>();
        for (ParentDeviceBindingEntity owner : owners) {
            if (owner == null || StringUtils.isBlank(owner.getDeviceId())) {
                continue;
            }
            String key = ParentDeviceAccessHelper.normalizeDeviceId(owner.getDeviceId());
            if (deviceKeys.contains(key)) {
                map.putIfAbsent(key, owner);
            }
        }
        return map;
    }

    private Map<String, Long> loadActiveMemberCounts(Set<String> deviceKeys) {
        if (deviceKeys.isEmpty()) {
            return Map.of();
        }
        List<ParentDeviceBindingEntity> bindings = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE));
        Map<String, Long> counts = new HashMap<>();
        for (ParentDeviceBindingEntity binding : bindings) {
            if (binding == null || StringUtils.isBlank(binding.getDeviceId())) {
                continue;
            }
            String key = ParentDeviceAccessHelper.normalizeDeviceId(binding.getDeviceId());
            if (deviceKeys.contains(key)) {
                counts.merge(key, 1L, Long::sum);
            }
        }
        return counts;
    }

    private static ParentDeviceBindingEntity findOwnerForDevice(
            String deviceId, Map<String, ParentDeviceBindingEntity> ownerByDeviceKey) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        return ownerByDeviceKey.get(ParentDeviceAccessHelper.normalizeDeviceId(deviceId));
    }

    /** 优先取已绑定家长的设备，否则取列表首台，用于解析家长端展示名 */
    private static DeviceEntity pickDisplayDevice(
            List<DeviceEntity> agentDeviceList, Map<String, ParentDeviceBindingEntity> ownerByDeviceKey) {
        if (agentDeviceList == null || agentDeviceList.isEmpty()) {
            return null;
        }
        for (DeviceEntity device : agentDeviceList) {
            if (findOwnerForDevice(device.getId(), ownerByDeviceKey) != null) {
                return device;
            }
        }
        return agentDeviceList.get(0);
    }

    private static long findMemberCount(String deviceId, Map<String, Long> memberCountByDeviceKey) {
        if (StringUtils.isBlank(deviceId)) {
            return 0;
        }
        return memberCountByDeviceKey.getOrDefault(ParentDeviceAccessHelper.normalizeDeviceId(deviceId), 0L);
    }

    private static void collectDeviceKeys(String deviceId, Set<String> keys) {
        if (StringUtils.isNotBlank(deviceId)) {
            keys.add(ParentDeviceAccessHelper.normalizeDeviceId(deviceId));
        }
    }

    private String resolveNickname(Long parentUserId, Map<Long, String> cache) {
        if (parentUserId == null) {
            return null;
        }
        if (cache.containsKey(parentUserId)) {
            return cache.get(parentUserId);
        }
        ParentUserEntity user = parentUserDao.selectById(parentUserId);
        String nickname = user != null && StringUtils.isNotBlank(user.getNickname())
                ? user.getNickname()
                : ("家长" + parentUserId);
        cache.put(parentUserId, nickname);
        return nickname;
    }

    private boolean checkAgentOwnedBy(Long scopeUserId, String agentId) {
        AgentEntity agent = agentDao.selectById(agentId);
        return agent != null && scopeUserId != null && scopeUserId.equals(agent.getUserId());
    }

    private static Long parseLongOrNull(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
