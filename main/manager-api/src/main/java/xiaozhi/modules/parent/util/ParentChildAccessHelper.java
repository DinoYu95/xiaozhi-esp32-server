package xiaozhi.modules.parent.util;

import org.apache.commons.lang3.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;

public final class ParentChildAccessHelper {

    private ParentChildAccessHelper() {
    }

    public static void ensureParentCanAccessChild(
            ParentDeviceBindingDao bindingDao, Long parentUserId, String deviceId) {
        ParentDeviceBindingEntity binding = bindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .and(ParentDeviceAccessHelper.deviceIdMatch(deviceId))
                        .last("LIMIT 1"));
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
    }

    public static DeviceChildEntity requireChild(DeviceChildDao childDao, Long childId) {
        DeviceChildEntity child = childDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        return child;
    }

    public static void ensureParentCanAccessChildById(
            DeviceChildDao childDao,
            ParentDeviceBindingDao bindingDao,
            Long parentUserId,
            Long childId) {
        DeviceChildEntity child = requireChild(childDao, childId);
        if (StringUtils.isBlank(child.getDeviceId())) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ensureParentCanAccessChild(bindingDao, parentUserId, child.getDeviceId());
    }
}
