package xiaozhi.modules.device.service.impl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.device.dto.DeviceTelemetryDTO;
import xiaozhi.modules.device.service.DeviceTelemetryService;
import xiaozhi.modules.device.vo.DeviceStatusCacheVO;

@Service
@RequiredArgsConstructor
public class DeviceTelemetryServiceImpl implements DeviceTelemetryService {

    private final RedisUtils redisUtils;

    @Override
    public void saveTelemetry(DeviceTelemetryDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getDeviceId())) {
            return;
        }
        DeviceStatusCacheVO existing = getStatus(dto.getDeviceId());
        DeviceStatusCacheVO cache = existing != null ? existing : new DeviceStatusCacheVO();

        if (dto.getBatteryLevel() != null) {
            int level = Math.max(0, Math.min(100, dto.getBatteryLevel()));
            cache.setBatteryLevel(level);
        }
        if (StringUtils.isNotBlank(dto.getWifiName())) {
            cache.setWifiName(dto.getWifiName().trim());
        }
        cache.setUpdatedAt(System.currentTimeMillis());

        String key = RedisKeys.getDeviceStatusKey(dto.getDeviceId());
        redisUtils.set(key, cache, STATUS_TTL_SECONDS);
    }

    @Override
    public DeviceStatusCacheVO getStatus(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        Object raw = redisUtils.get(RedisKeys.getDeviceStatusKey(deviceId));
        if (raw instanceof DeviceStatusCacheVO vo) {
            return vo;
        }
        return null;
    }
}
