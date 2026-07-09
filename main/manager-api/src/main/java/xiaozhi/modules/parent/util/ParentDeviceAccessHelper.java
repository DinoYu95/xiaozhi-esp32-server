package xiaozhi.modules.parent.util;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;

public final class ParentDeviceAccessHelper {

    private ParentDeviceAccessHelper() {
    }

    public static String normalizeDeviceId(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return deviceId;
        }
        return deviceId.replace(":", "_").toLowerCase();
    }

    public static ParentDeviceBindingEntity findActiveBinding(
            ParentDeviceBindingDao bindingDao, Long parentUserId, String deviceId) {
        if (parentUserId == null || StringUtils.isBlank(deviceId)) {
            return null;
        }
        String normalized = normalizeDeviceId(deviceId);
        return bindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
    }

    public static ParentDeviceBindingEntity requireActiveBinding(
            ParentDeviceBindingDao bindingDao, Long parentUserId, String deviceId) {
        ParentDeviceBindingEntity binding = findActiveBinding(bindingDao, parentUserId, deviceId);
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        return binding;
    }

    public static boolean isOwner(ParentDeviceBindingEntity binding) {
        return binding != null
                && ParentDeviceBindingEntity.ROLE_OWNER.equalsIgnoreCase(StringUtils.trimToEmpty(binding.getRole()));
    }

    public static ParentDeviceBindingEntity requireOwner(
            ParentDeviceBindingDao bindingDao, Long parentUserId, String deviceId) {
        ParentDeviceBindingEntity binding = requireActiveBinding(bindingDao, parentUserId, deviceId);
        if (!isOwner(binding)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_OWNER);
        }
        return binding;
    }

    public static void requireOwnerWrite(
            ParentDeviceBindingDao bindingDao, Long parentUserId, String deviceId) {
        ParentDeviceBindingEntity binding = requireActiveBinding(bindingDao, parentUserId, deviceId);
        if (!isOwner(binding)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_MEMBER_READONLY);
        }
    }

    public static long countActiveMembers(ParentDeviceBindingDao bindingDao, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return 0;
        }
        String normalized = normalizeDeviceId(deviceId);
        return bindingDao.selectCount(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
    }

    public static ParentDeviceBindingEntity findPrimaryOwner(
            ParentDeviceBindingDao bindingDao, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        String normalized = normalizeDeviceId(deviceId);
        return bindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .eq(ParentDeviceBindingEntity::getRole, ParentDeviceBindingEntity.ROLE_OWNER)
                        .eq(ParentDeviceBindingEntity::getIsPrimary, 1)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized))
                        .last("LIMIT 1"));
    }

    public static ParentDeviceBindingEntity findAnyBinding(
            ParentDeviceBindingDao bindingDao, Long parentUserId, String deviceId) {
        if (parentUserId == null || StringUtils.isBlank(deviceId)) {
            return null;
        }
        String normalized = normalizeDeviceId(deviceId);
        return bindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized))
                        .last("LIMIT 1"));
    }

    public static List<ParentDeviceBindingEntity> findActiveBindingsForDevice(
            ParentDeviceBindingDao bindingDao, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return List.of();
        }
        String normalized = normalizeDeviceId(deviceId);
        return bindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
    }

    /** 兼容 device_id 存 MAC 冒号/下划线两种格式 */
    public static DeviceChildEntity findDeviceChild(DeviceChildDao deviceChildDao, String deviceId) {
        if (deviceChildDao == null || StringUtils.isBlank(deviceId)) {
            return null;
        }
        String normalized = normalizeDeviceId(deviceId);
        String colonForm = deviceId.contains("_") && !deviceId.contains(":")
                ? deviceId.replace('_', ':')
                : deviceId;
        return deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>()
                        .and(w -> w.eq(DeviceChildEntity::getDeviceId, deviceId)
                                .or().eq(DeviceChildEntity::getDeviceId, normalized)
                                .or().eq(DeviceChildEntity::getDeviceId, colonForm)
                                .or().apply("REPLACE(LOWER(device_id), ':', '_') = {0}", normalized))
                        .last("LIMIT 1"));
    }
}
