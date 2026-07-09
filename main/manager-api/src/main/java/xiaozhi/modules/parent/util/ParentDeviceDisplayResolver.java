package xiaozhi.modules.parent.util;

import org.apache.commons.lang3.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;

/**
 * 设备展示名解析：Owner / Member 共用同一套规则，保证 deviceName 一致且非空。
 */
public final class ParentDeviceDisplayResolver {

    private ParentDeviceDisplayResolver() {
    }

    public static DeviceEntity resolveDevice(DeviceDao deviceDao, String deviceId) {
        if (deviceDao == null || StringUtils.isBlank(deviceId)) {
            return null;
        }
        DeviceEntity device = deviceDao.selectById(deviceId.trim());
        if (device != null) {
            return device;
        }
        return deviceDao.selectByIdOrMacVariant(deviceId.trim());
    }

    /**
     * 将 binding 中的 deviceId 规范化为 ai_device.id（接受邀请、列表展示时写入/返回）。
     */
    public static String canonicalDeviceId(DeviceDao deviceDao, String deviceId) {
        DeviceEntity device = resolveDevice(deviceDao, deviceId);
        return device != null ? device.getId() : StringUtils.trimToEmpty(deviceId);
    }

    public static String resolveDeviceName(
            DeviceDao deviceDao,
            DeviceChildDao deviceChildDao,
            ParentDeviceBindingDao bindingDao,
            String deviceId) {
        DeviceEntity device = resolveDevice(deviceDao, deviceId);
        if (device == null && bindingDao != null) {
            device = resolveDeviceViaSiblingBinding(deviceDao, bindingDao, deviceId);
        }
        String canonicalId = device != null ? device.getId() : deviceId;
        DeviceChildEntity child = ParentDeviceAccessHelper.findDeviceChild(deviceChildDao, canonicalId);
        if (child == null && StringUtils.isNotBlank(deviceId) && !deviceId.equals(canonicalId)) {
            child = ParentDeviceAccessHelper.findDeviceChild(deviceChildDao, deviceId);
        }
        String name = buildDisplayName(device, child);
        return StringUtils.defaultIfBlank(name, "我的机器人");
    }

    private static DeviceEntity resolveDeviceViaSiblingBinding(
            DeviceDao deviceDao,
            ParentDeviceBindingDao bindingDao,
            String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        String normalized = ParentDeviceAccessHelper.normalizeDeviceId(deviceId);
        ParentDeviceBindingEntity ownerBinding = ParentDeviceAccessHelper.findPrimaryOwner(bindingDao, deviceId);
        if (ownerBinding == null) {
            ownerBinding = bindingDao.selectOne(
                    new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                            .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                            .eq(ParentDeviceBindingEntity::getRole, ParentDeviceBindingEntity.ROLE_OWNER)
                            .eq(ParentDeviceBindingEntity::getIsPrimary, 1)
                            .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                    .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized))
                            .last("LIMIT 1"));
        }
        if (ownerBinding != null) {
            DeviceEntity device = resolveDevice(deviceDao, ownerBinding.getDeviceId());
            if (device != null) {
                return device;
            }
        }
        return null;
    }

    private static String buildDisplayName(DeviceEntity device, DeviceChildEntity child) {
        if (device != null && StringUtils.isNotBlank(device.getAlias())) {
            return device.getAlias().trim();
        }
        if (child != null && StringUtils.isNotBlank(child.getName())) {
            return child.getName().trim() + "的机器人";
        }
        return "我的机器人";
    }
}
