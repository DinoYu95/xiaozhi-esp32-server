package xiaozhi.modules.parent.util;

import org.apache.commons.lang3.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.AllArgsConstructor;
import lombok.Getter;
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

    @Getter
    @AllArgsConstructor
    public static class DeviceDisplay {
        private final String canonicalDeviceId;
        private final String deviceName;
        private final String ownerChildName;
        private final DeviceEntity device;
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

    /**
     * Owner / Member 统一解析设备展示字段（deviceName、ownerChildName、canonicalDeviceId）。
     */
    public static DeviceDisplay resolveDisplay(
            DeviceDao deviceDao,
            DeviceChildDao deviceChildDao,
            ParentDeviceBindingDao bindingDao,
            String deviceId) {
        return resolveDisplayForBinding(deviceDao, deviceChildDao, bindingDao, deviceId, null);
    }

    /**
     * 按 binding 解析展示信息；Member 在 device_id 格式不一致时回退到邀请人 Owner 绑定。
     */
    public static DeviceDisplay resolveDisplayForBinding(
            DeviceDao deviceDao,
            DeviceChildDao deviceChildDao,
            ParentDeviceBindingDao bindingDao,
            String deviceId,
            ParentDeviceBindingEntity binding) {
        String lookupDeviceId = resolveLookupDeviceId(deviceDao, bindingDao, deviceId, binding);
        DeviceEntity device = resolveDevice(deviceDao, lookupDeviceId);
        if (device == null && bindingDao != null) {
            device = resolveDeviceViaSiblingBinding(deviceDao, bindingDao, lookupDeviceId);
        }
        String canonicalId = device != null ? device.getId() : canonicalDeviceId(deviceDao, lookupDeviceId);
        if (device == null && StringUtils.isNotBlank(canonicalId)) {
            device = resolveDevice(deviceDao, canonicalId);
        }
        DeviceChildEntity child = findDeviceChild(deviceChildDao, canonicalId, lookupDeviceId);
        String ownerChildName = extractChildName(child);
        String deviceName = StringUtils.defaultIfBlank(buildDisplayName(device, child), "我的机器人");
        return new DeviceDisplay(canonicalId, deviceName, ownerChildName, device);
    }

    private static String resolveLookupDeviceId(
            DeviceDao deviceDao,
            ParentDeviceBindingDao bindingDao,
            String deviceId,
            ParentDeviceBindingEntity binding) {
        if (StringUtils.isNotBlank(deviceId) && resolveDevice(deviceDao, deviceId) != null) {
            DeviceEntity device = resolveDevice(deviceDao, deviceId);
            return device != null ? device.getId() : deviceId;
        }
        if (binding != null
                && ParentDeviceBindingEntity.ROLE_MEMBER.equalsIgnoreCase(StringUtils.trimToEmpty(binding.getRole()))
                && bindingDao != null) {
            ParentDeviceBindingEntity ownerBinding =
                    ParentDeviceAccessHelper.findInviterOwnerBinding(bindingDao, binding);
            if (ownerBinding != null && StringUtils.isNotBlank(ownerBinding.getDeviceId())) {
                DeviceEntity device = resolveDevice(deviceDao, ownerBinding.getDeviceId());
                if (device != null) {
                    return device.getId();
                }
                return ownerBinding.getDeviceId();
            }
        }
        return deviceId;
    }

    public static String resolveDeviceName(
            DeviceDao deviceDao,
            DeviceChildDao deviceChildDao,
            ParentDeviceBindingDao bindingDao,
            String deviceId) {
        return resolveDisplay(deviceDao, deviceChildDao, bindingDao, deviceId).getDeviceName();
    }

    private static DeviceChildEntity findDeviceChild(
            DeviceChildDao deviceChildDao, String canonicalId, String fallbackDeviceId) {
        DeviceChildEntity child = ParentDeviceAccessHelper.findDeviceChild(deviceChildDao, canonicalId);
        if (child == null && StringUtils.isNotBlank(fallbackDeviceId) && !fallbackDeviceId.equals(canonicalId)) {
            child = ParentDeviceAccessHelper.findDeviceChild(deviceChildDao, fallbackDeviceId);
        }
        return child;
    }

    private static String extractChildName(DeviceChildEntity child) {
        if (child == null || StringUtils.isBlank(child.getName())) {
            return null;
        }
        return child.getName().trim();
    }

    private static DeviceEntity resolveDeviceViaSiblingBinding(
            DeviceDao deviceDao,
            ParentDeviceBindingDao bindingDao,
            String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        ParentDeviceBindingEntity ownerBinding = ParentDeviceAccessHelper.findPrimaryOwner(
                bindingDao, deviceId);
        if (ownerBinding == null) {
            ownerBinding = bindingDao.selectOne(
                    new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                            .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                            .eq(ParentDeviceBindingEntity::getRole, ParentDeviceBindingEntity.ROLE_OWNER)
                            .and(ParentDeviceAccessHelper.deviceIdMatch(deviceId))
                            .orderByDesc(ParentDeviceBindingEntity::getIsPrimary)
                            .last("LIMIT 1"));
        }
        if (ownerBinding != null) {
            DeviceEntity device = resolveDevice(deviceDao, ownerBinding.getDeviceId());
            if (device != null) {
                return device;
            }
        }
        for (ParentDeviceBindingEntity binding :
                ParentDeviceAccessHelper.findActiveBindingsForDevice(bindingDao, deviceId)) {
            DeviceEntity device = resolveDevice(deviceDao, binding.getDeviceId());
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
