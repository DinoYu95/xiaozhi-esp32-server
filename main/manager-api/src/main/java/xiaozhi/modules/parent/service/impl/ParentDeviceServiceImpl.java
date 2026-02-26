package xiaozhi.modules.parent.service.impl;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dto.ParentDeviceBindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceUnbindDTO;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.service.ParentDeviceService;
import xiaozhi.modules.parent.vo.ParentDeviceItemVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
public class ParentDeviceServiceImpl implements ParentDeviceService {

    private static final String PARAM_DEFAULT_AGENT_ID = "parent.default_agent_id";

    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final DeviceDao deviceDao;
    private final RedisUtils redisUtils;
    private final SysParamsService sysParamsService;

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

        // 是否已被其他家长绑定
        ParentDeviceBindingEntity existing = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getDeviceId, deviceId));
        if (existing != null && !existing.getParentUserId().equals(parentUserId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_ALREADY_BOUND);
        }
        if (existing != null && existing.getParentUserId().equals(parentUserId)) {
            redisUtils.delete(List.of(cacheDeviceKey, deviceKey));
            return new BindResult(deviceId, "已绑定");
        }

        // ai_device 不存在则创建
        if (deviceDao.selectById(deviceId) == null) {
            String defaultAgentId = sysParamsService.getValue(PARAM_DEFAULT_AGENT_ID, true);
            if (StringUtils.isBlank(defaultAgentId)) {
                defaultAgentId = "1"; // 兜底
            }
            String macAddress = (String) cacheMap.get("mac_address");
            String board = (String) cacheMap.get("board");
            String appVersion = (String) cacheMap.get("app_version");
            Date now = new Date();
            DeviceEntity deviceEntity = new DeviceEntity();
            deviceEntity.setId(deviceId);
            deviceEntity.setBoard(board != null ? board : "unknown");
            deviceEntity.setAgentId(defaultAgentId);
            deviceEntity.setAppVersion(appVersion);
            deviceEntity.setMacAddress(macAddress != null ? macAddress : deviceId);
            deviceEntity.setAutoUpdate(1);
            deviceEntity.setCreateDate(now);
            deviceEntity.setUpdateDate(now);
            deviceEntity.setLastConnectedAt(now);
            deviceDao.insert(deviceEntity);
        }

        ParentDeviceBindingEntity binding = new ParentDeviceBindingEntity();
        binding.setParentUserId(parentUserId);
        binding.setDeviceId(deviceId);
        binding.setBindTime(new Date());
        binding.setBindSource("code");
        binding.setCreateTime(new Date());
        parentDeviceBindingDao.insert(binding);

        redisUtils.delete(List.of(cacheDeviceKey, deviceKey));
        return new BindResult(deviceId, "绑定成功");
    }

    @Override
    public void unbind(Long parentUserId, ParentDeviceUnbindDTO dto) {
        if (StringUtils.isBlank(dto.getDeviceId())) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getDeviceId, dto.getDeviceId())
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId));
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        parentDeviceBindingDao.deleteById(binding.getId());
    }

    @Override
    public List<ParentDeviceItemVO> list(Long parentUserId) {
        List<ParentDeviceBindingEntity> list = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .orderByDesc(ParentDeviceBindingEntity::getBindTime));
        return list.stream().map(b -> {
            ParentDeviceItemVO vo = new ParentDeviceItemVO();
            vo.setDeviceId(b.getDeviceId());
            vo.setBindTime(b.getBindTime());
            return vo;
        }).collect(Collectors.toList());
    }
}
