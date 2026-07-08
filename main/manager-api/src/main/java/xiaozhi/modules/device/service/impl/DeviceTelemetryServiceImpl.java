package xiaozhi.modules.device.service.impl;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.dto.DeviceTelemetryDTO;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceTelemetryService;
import xiaozhi.modules.device.vo.DeviceStatusCacheVO;

@Service
@RequiredArgsConstructor
public class DeviceTelemetryServiceImpl implements DeviceTelemetryService {

    private final RedisUtils redisUtils;
    private final DeviceDao deviceDao;

    @Override
    public void saveTelemetry(DeviceTelemetryDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getDeviceId())) {
            return;
        }
        String deviceId = dto.getDeviceId().trim();
        DeviceStatusCacheVO existing = getStatus(deviceId);
        DeviceStatusCacheVO cache = existing != null ? existing : new DeviceStatusCacheVO();

        if (dto.getBatteryLevel() != null) {
            int level = Math.max(0, Math.min(100, dto.getBatteryLevel()));
            cache.setBatteryLevel(level);
        }
        if (StringUtils.isNotBlank(dto.getWifiName())) {
            cache.setWifiName(dto.getWifiName().trim());
        }
        cache.setUpdatedAt(System.currentTimeMillis());

        // Redis：保留最近一次上报，不设 TTL（离线后仍可读）
        String key = RedisKeys.getDeviceStatusKey(deviceId);
        redisUtils.set(key, cache, RedisUtils.NOT_EXPIRE);

        persistToDevice(deviceId, cache);
    }

    @Override
    public DeviceStatusCacheVO getStatus(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        Object raw = redisUtils.get(RedisKeys.getDeviceStatusKey(deviceId));
        if (raw instanceof DeviceStatusCacheVO vo) {
            if (vo.getBatteryLevel() != null || StringUtils.isNotBlank(vo.getWifiName())) {
                return vo;
            }
        }
        return loadFromDevice(deviceId);
    }

    private void persistToDevice(String deviceId, DeviceStatusCacheVO cache) {
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null) {
            device = deviceDao.selectByIdOrMacVariant(deviceId);
        }
        if (device == null) {
            return;
        }
        boolean batteryChanged = cache.getBatteryLevel() != null
                && !cache.getBatteryLevel().equals(device.getBatteryLevel());
        boolean wifiChanged = StringUtils.isNotBlank(cache.getWifiName())
                && !cache.getWifiName().equals(StringUtils.trimToEmpty(device.getWifiName()));
        if (!batteryChanged && !wifiChanged) {
            return;
        }
        if (cache.getBatteryLevel() != null) {
            device.setBatteryLevel(cache.getBatteryLevel());
        }
        if (StringUtils.isNotBlank(cache.getWifiName())) {
            device.setWifiName(cache.getWifiName());
        }
        device.setTelemetryUpdatedAt(new Date());
        device.setUpdateDate(new Date());
        deviceDao.updateById(device);
    }

    private DeviceStatusCacheVO loadFromDevice(String deviceId) {
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null) {
            device = deviceDao.selectByIdOrMacVariant(deviceId);
        }
        if (device == null) {
            return null;
        }
        if (device.getBatteryLevel() == null && StringUtils.isBlank(device.getWifiName())) {
            return null;
        }
        DeviceStatusCacheVO vo = new DeviceStatusCacheVO();
        vo.setBatteryLevel(device.getBatteryLevel());
        vo.setWifiName(device.getWifiName());
        if (device.getTelemetryUpdatedAt() != null) {
            vo.setUpdatedAt(device.getTelemetryUpdatedAt().getTime());
        }
        return vo;
    }
}
