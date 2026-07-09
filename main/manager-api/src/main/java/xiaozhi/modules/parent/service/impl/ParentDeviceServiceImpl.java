package xiaozhi.modules.parent.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.agent.dto.AgentCreateDTO;
import xiaozhi.modules.agent.service.AgentService;
import xiaozhi.modules.agent.service.AgentSkillMappingService;
import xiaozhi.modules.agent.service.AgentSkillService;
import xiaozhi.modules.agent.vo.AgentSkillVO;
import xiaozhi.modules.agent.vo.AgentSkillMappingVO;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.dto.ParentDeviceBindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceNameUpdateDTO;
import xiaozhi.modules.parent.dto.ParentDeviceSkillBindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceUnbindDTO;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.service.DeviceInviteService;
import xiaozhi.modules.parent.service.ParentDeviceService;
import xiaozhi.modules.parent.service.ParentUserSkillService;
import xiaozhi.modules.parent.util.ParentDeviceAccessHelper;
import xiaozhi.modules.parent.util.ParentDeviceDisplayResolver;
import xiaozhi.modules.parent.vo.ParentDeviceItemVO;
import xiaozhi.modules.parent.vo.ParentDeviceSkillVO;
import xiaozhi.modules.parent.vo.ParentUserSkillVO;
import xiaozhi.modules.device.service.DeviceTelemetryService;
import xiaozhi.modules.device.vo.DeviceStatusCacheVO;
import xiaozhi.modules.sys.service.SysParamsService;
import xiaozhi.modules.sys.service.SysUserScopeService;

@Service
@RequiredArgsConstructor
public class ParentDeviceServiceImpl implements ParentDeviceService {

    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final DeviceDao deviceDao;
    private final DeviceChildDao deviceChildDao;
    private final RedisUtils redisUtils;
    private final SysParamsService sysParamsService;
    private final AgentService agentService;
    private final AgentSkillService agentSkillService;
    private final AgentSkillMappingService agentSkillMappingService;
    private final ParentUserDao parentUserDao;
    private final ParentUserSkillService parentUserSkillService;
    private final SysUserScopeService sysUserScopeService;
    private final DeviceTelemetryService deviceTelemetryService;
    private final DeviceInviteService deviceInviteService;

    @Override
    public BindResult bind(Long parentUserId, ParentDeviceBindDTO dto) {
        if (StringUtils.isBlank(dto.getCode())) {
            throw new RenException(ErrorCode.PARENT_BIND_CODE_INVALID);
        }
        String deviceKey = RedisKeys.getOtaActivationCode(dto.getCode());
        Object cacheDeviceId = redisUtils.get(deviceKey);
        if (cacheDeviceId == null || StringUtils.isBlank(cacheDeviceId.toString())) {
            throw new RenException(ErrorCode.PARENT_BIND_CODE_INVALID);
        }
        String deviceId = (String) cacheDeviceId;
        String safeDeviceId = deviceId.replace(":", "_").toLowerCase();
        String cacheDeviceKey = RedisKeys.getOtaDeviceActivationInfo(safeDeviceId);
        Map<String, Object> cacheMap = (Map<String, Object>) redisUtils.get(cacheDeviceKey);
        if (cacheMap == null || cacheMap.isEmpty()) {
            throw new RenException(ErrorCode.PARENT_BIND_CODE_INVALID);
        }
        String cachedCode = (String) cacheMap.get("activation_code");
        if (!dto.getCode().equals(cachedCode)) {
            throw new RenException(ErrorCode.PARENT_BIND_CODE_INVALID);
        }

        // 设备是否已有 active 绑定（首绑后需走邀请链路）
        long activeMemberCount = ParentDeviceAccessHelper.countActiveMembers(parentDeviceBindingDao, deviceId);
        ParentDeviceBindingEntity myActive = ParentDeviceAccessHelper.findActiveBinding(
                parentDeviceBindingDao, parentUserId, deviceId);
        if (myActive != null) {
            redisUtils.delete(List.of(cacheDeviceKey, deviceKey));
            return new BindResult(deviceId, "已绑定");
        }
        if (activeMemberCount > 0) {
            throw new RenException(ErrorCode.PARENT_DEVICE_ALREADY_BOUND);
        }

        // ai_device 不存在则创建（同时自动为固定后台用户创建一个默认智能体）
        if (deviceDao.selectById(deviceId) == null) {
            // 生成智能体名称：{家长昵称}的agent
            ParentUserEntity parentUser = parentUserDao.selectById(parentUserId);
            String nickname = parentUser != null && StringUtils.isNotBlank(parentUser.getNickname())
                    ? parentUser.getNickname()
                    : "家长";
            AgentCreateDTO agentCreateDTO = new AgentCreateDTO();
            agentCreateDTO.setAgentName(nickname + "的agent");
            Long platformOwnerUserId = sysUserScopeService.getPlatformOwnerUserId();
            String agentId = agentService.createAgentForDeviceBind(platformOwnerUserId, agentCreateDTO);

            String macAddress = (String) cacheMap.get("mac_address");
            String board = (String) cacheMap.get("board");
            String appVersion = (String) cacheMap.get("app_version");
            Date now = new Date();
            DeviceEntity deviceEntity = new DeviceEntity();
            deviceEntity.setId(deviceId);
            deviceEntity.setBoard(board != null ? board : "unknown");
            deviceEntity.setAgentId(agentId);
            deviceEntity.setAppVersion(appVersion);
            deviceEntity.setMacAddress(macAddress != null ? macAddress : deviceId);
            deviceEntity.setAutoUpdate(1);
            deviceEntity.setUserId(platformOwnerUserId);
            deviceEntity.setCreator(platformOwnerUserId);
            deviceEntity.setCreateDate(now);
            deviceEntity.setUpdateDate(now);
            deviceEntity.setLastConnectedAt(now);
            deviceDao.insert(deviceEntity);
            // 为新建 agent 自动配置官方推荐技能
            agentSkillMappingService.addOfficialRecommendedSkillsIfEmpty(agentId);
        }

        Date bindNow = new Date();
        ParentDeviceBindingEntity anyBinding = ParentDeviceAccessHelper.findAnyBinding(
                parentDeviceBindingDao, parentUserId, deviceId);
        if (anyBinding != null) {
            anyBinding.setRole(ParentDeviceBindingEntity.ROLE_OWNER);
            anyBinding.setIsPrimary(1);
            anyBinding.setInvitedBy(null);
            anyBinding.setStatus(ParentDeviceBindingEntity.STATUS_ACTIVE);
            anyBinding.setBindTime(bindNow);
            anyBinding.setBindSource(StringUtils.isNotBlank(dto.getBindSource()) ? dto.getBindSource() : "code");
            anyBinding.setUpdatedAt(bindNow);
            parentDeviceBindingDao.updateById(anyBinding);
        } else {
            ParentDeviceBindingEntity binding = new ParentDeviceBindingEntity();
            binding.setParentUserId(parentUserId);
            binding.setDeviceId(deviceId);
            binding.setBindTime(bindNow);
            binding.setBindSource(StringUtils.isNotBlank(dto.getBindSource()) ? dto.getBindSource() : "code");
            binding.setRole(ParentDeviceBindingEntity.ROLE_OWNER);
            binding.setIsPrimary(1);
            binding.setStatus(ParentDeviceBindingEntity.STATUS_ACTIVE);
            binding.setCreateTime(bindNow);
            binding.setUpdatedAt(bindNow);
            parentDeviceBindingDao.insert(binding);
        }

        redisUtils.delete(List.of(cacheDeviceKey, deviceKey));
        return new BindResult(deviceId, "绑定成功");
    }

    @Override
    public void unbind(Long parentUserId, ParentDeviceUnbindDTO dto) {
        if (StringUtils.isBlank(dto.getDeviceId())) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ParentDeviceBindingEntity binding = ParentDeviceAccessHelper.requireActiveBinding(
                parentDeviceBindingDao, parentUserId, dto.getDeviceId());
        String deviceId = binding.getDeviceId();
        if (ParentDeviceAccessHelper.isOwner(binding)) {
            List<ParentDeviceBindingEntity> all =
                    ParentDeviceAccessHelper.findActiveBindingsForDevice(parentDeviceBindingDao, deviceId);
            for (ParentDeviceBindingEntity b : all) {
                parentDeviceBindingDao.deleteById(b.getId());
            }
            deviceInviteService.revokeActiveInvitesForDevice(deviceId);
        } else {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_OWNER);
        }
    }

    private static final long ONLINE_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5);

    @Override
    public List<ParentDeviceItemVO> list(Long parentUserId) {
        List<ParentDeviceBindingEntity> list = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .orderByDesc(ParentDeviceBindingEntity::getBindTime));
        Date now = new Date();
        return list.stream().map(b -> {
            ParentDeviceItemVO vo = new ParentDeviceItemVO();
            String bindingDeviceId = b.getDeviceId();
            ParentDeviceDisplayResolver.DeviceDisplay display = ParentDeviceDisplayResolver.resolveDisplayForBinding(
                    deviceDao, deviceChildDao, parentDeviceBindingDao, bindingDeviceId, b);
            String canonicalDeviceId = display.getCanonicalDeviceId();
            DeviceEntity device = display.getDevice();
            vo.setDeviceId(canonicalDeviceId);
            vo.setBindTime(b.getBindTime());
            vo.setOwnerChildName(display.getOwnerChildName());
            vo.setDeviceName(display.getDeviceName());
            if (device != null && StringUtils.isNotBlank(canonicalDeviceId)
                    && !ParentDeviceAccessHelper.deviceIdsEquivalent(bindingDeviceId, canonicalDeviceId)) {
                b.setDeviceId(canonicalDeviceId);
                b.setUpdatedAt(new Date());
                parentDeviceBindingDao.updateById(b);
            }
            // 设备最后连接时间、在线状态
            if (device != null) {
                vo.setLastConnectedAt(device.getLastConnectedAt());
                vo.setIsOnline(device.getLastConnectedAt() != null
                        && (now.getTime() - device.getLastConnectedAt().getTime()) < ONLINE_THRESHOLD_MS);
            } else {
                vo.setIsOnline(false);
            }
            // 电量、WiFi：从 Redis 读取设备上报缓存，无数据时占位
            DeviceStatusCacheVO status = deviceTelemetryService.getStatus(canonicalDeviceId);
            if (status != null && status.getBatteryLevel() != null) {
                vo.setBatteryLevel(status.getBatteryLevel());
            } else {
                vo.setBatteryLevel(0);
            }
            if (status != null && StringUtils.isNotBlank(status.getWifiName())) {
                vo.setWifiName(status.getWifiName());
            } else {
                vo.setWifiName("--");
            }
            String role = StringUtils.isNotBlank(b.getRole()) ? b.getRole() : ParentDeviceBindingEntity.ROLE_OWNER;
            vo.setRole(role);
            vo.setIsPrimaryOwner(b.getIsPrimary() != null && b.getIsPrimary() == 1);
            vo.setCanInvite(ParentDeviceBindingEntity.ROLE_OWNER.equalsIgnoreCase(role));
            vo.setMemberCount((int) ParentDeviceAccessHelper.countActiveMembers(
                    parentDeviceBindingDao, canonicalDeviceId));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void updateDeviceName(Long parentUserId, String deviceId, ParentDeviceNameUpdateDTO dto) {
        if (StringUtils.isBlank(deviceId) || dto == null || StringUtils.isBlank(dto.getDeviceName())) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ParentDeviceAccessHelper.requireOwnerWrite(parentDeviceBindingDao, parentUserId, deviceId);
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null) {
            device = deviceDao.selectByIdOrMacVariant(deviceId);
        }
        if (device == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        device.setAlias(dto.getDeviceName().trim());
        device.setUpdateDate(new Date());
        deviceDao.updateById(device);
    }

    @Override
    public List<ParentDeviceSkillVO> listSkills(Long parentUserId, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ParentDeviceBindingEntity binding = ParentDeviceAccessHelper.requireActiveBinding(
                parentDeviceBindingDao, parentUserId, deviceId);
        deviceId = binding.getDeviceId();
        // 获取设备的 agentId（兼容 deviceId 格式：B6:C8:35:D6:10:48 / b6_c8_35_d6_10_48）
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null) {
            device = deviceDao.selectByIdOrMacVariant(deviceId);
        }
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            return new ArrayList<>();
        }
        // 获取 agent 的 skill 映射
        List<AgentSkillMappingVO> mappings = agentSkillMappingService.listByAgentId(device.getAgentId());
        if (mappings == null || mappings.isEmpty()) {
            return new ArrayList<>();
        }
        // 按 skillId 分组 speakerTypes，并获取技能详情
        Map<String, List<String>> skillIdToSpeakerTypes = new LinkedHashMap<>();
        for (AgentSkillMappingVO m : mappings) {
            if (StringUtils.isNotBlank(m.getSkillId())) {
                skillIdToSpeakerTypes
                        .computeIfAbsent(m.getSkillId(), k -> new ArrayList<>())
                        .add(m.getSpeakerType() != null ? m.getSpeakerType() : "");
            }
        }
        List<ParentDeviceSkillVO> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : skillIdToSpeakerTypes.entrySet()) {
            String skillId = e.getKey();
            List<String> speakerTypes = e.getValue();
            if (skillId.startsWith("parent_")) {
                Long parentSkillId = parseParentSkillId(skillId);
                if (parentSkillId != null) {
                    ParentUserSkillVO skill = parentUserSkillService.getById(parentSkillId);
                    if (skill != null) {
                        ParentDeviceSkillVO vo = new ParentDeviceSkillVO();
                        vo.setSkillId(skill.getId());
                        vo.setName(skill.getName());
                        vo.setDescription(skill.getDescription());
                        vo.setInstructions(skill.getInstructions());
                        vo.setVersion(skill.getVersion());
                        vo.setTools(skill.getTools());
                        vo.setMetadata(skill.getMetadata());
                        vo.setSkillSource("parent");
                        vo.setSpeakerTypes(speakerTypes);
                        vo.setCreateTime(skill.getCreateTime());
                        vo.setUpdateTime(skill.getUpdateTime());
                        result.add(vo);
                    }
                }
            } else {
                AgentSkillVO skill = agentSkillService.getById(skillId);
                if (skill != null) {
                    ParentDeviceSkillVO vo = new ParentDeviceSkillVO();
                    vo.setSkillId(skill.getId());
                    vo.setName(skill.getName());
                    vo.setDescription(skill.getDescription());
                    vo.setInstructions(skill.getInstructions());
                    vo.setVersion(skill.getVersion());
                    vo.setTools(skill.getTools());
                    vo.setMetadata(skill.getMetadata());
                    vo.setSkillSource("official");
                    vo.setSpeakerTypes(speakerTypes);
                    vo.setCreateTime(skill.getCreateTime());
                    vo.setUpdateTime(skill.getUpdateTime());
                    result.add(vo);
                }
            }
        }
        return result;
    }

    @Override
    public void bindSkill(Long parentUserId, String deviceId, ParentDeviceSkillBindDTO dto) {
        if (StringUtils.isBlank(deviceId) || dto == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ensureDeviceBoundAndGetAgentId(parentUserId, deviceId);
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null) device = deviceDao.selectByIdOrMacVariant(deviceId);
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        String speakerType = normalizeSpeakerType(dto.getSpeakerType());
        String storageSkillId = resolveStorageSkillId(dto.getSkillSource(), dto.getSkillId(), parentUserId);
        if (StringUtils.isBlank(storageSkillId)) {
            throw new RenException(ErrorCode.PARENT_SKILL_NOT_FOUND);
        }
        agentSkillMappingService.addMapping(device.getAgentId(), speakerType, storageSkillId);
    }

    @Override
    public void unbindSkill(Long parentUserId, String deviceId, String skillSource, Object skillId, String speakerType) {
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ensureDeviceBoundAndGetAgentId(parentUserId, deviceId);
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null) device = deviceDao.selectByIdOrMacVariant(deviceId);
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        String storageSkillId = toStorageSkillId(skillSource, skillId);
        if (StringUtils.isBlank(storageSkillId)) {
            throw new RenException(ErrorCode.PARENT_SKILL_NOT_FOUND);
        }
        agentSkillMappingService.removeMapping(device.getAgentId(),
                normalizeSpeakerType(speakerType), storageSkillId);
    }

    /** 解绑时仅做格式转换，不校验技能是否存在 */
    private static String toStorageSkillId(String skillSource, Object skillId) {
        if (skillId == null) return null;
        if ("parent".equalsIgnoreCase(StringUtils.trimToEmpty(skillSource))) {
            long id = skillId instanceof Number ? ((Number) skillId).longValue()
                    : Long.parseLong(skillId.toString().trim());
            return "parent_" + id;
        }
        return skillId.toString().trim();
    }

    private void ensureDeviceBoundAndGetAgentId(Long parentUserId, String deviceId) {
        ParentDeviceAccessHelper.requireActiveBinding(parentDeviceBindingDao, parentUserId, deviceId);
    }

    private static String normalizeSpeakerType(String speakerType) {
        if (StringUtils.isBlank(speakerType)) return "owner_child";
        String s = speakerType.trim().toLowerCase();
        if (s.matches("owner_child|parent|other_child|other_adult|unknown")) return s;
        return "owner_child";
    }

    /** 将请求 skillId 转为 ai_agent_skill_mapping 存储格式：官方=原样，家长=parent_{id} */
    private String resolveStorageSkillId(String skillSource, Object skillId, Long parentUserId) {
        if (skillId == null) return null;
        if ("parent".equalsIgnoreCase(skillSource != null ? skillSource.trim() : "")) {
            Long id = skillId instanceof Number ? ((Number) skillId).longValue()
                    : Long.parseLong(skillId.toString().trim());
            if (parentUserSkillService.getByIdAndParentUserId(id, parentUserId) == null) return null;
            return "parent_" + id;
        }
        String id = skillId.toString().trim();
        if (agentSkillService.getById(id) == null) return null;
        return id;
    }

    @Override
    public List<Object> listBoundSkillIds(Long parentUserId, String deviceId) {
        List<ParentDeviceSkillVO> skills = listSkills(parentUserId, deviceId);
        if (skills == null || skills.isEmpty()) return new ArrayList<>();
        return skills.stream().map(ParentDeviceSkillVO::getSkillId).filter(sid -> sid != null).collect(Collectors.toList());
    }

    private static Long parseParentSkillId(String skillId) {
        if (skillId == null || !skillId.startsWith("parent_")) return null;
        try {
            return Long.parseLong(skillId.substring(7));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
