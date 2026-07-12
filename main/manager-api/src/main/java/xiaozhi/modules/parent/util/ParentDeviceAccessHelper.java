package xiaozhi.modules.parent.util;

import java.util.List;
import java.util.function.Consumer;

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

    public static String toColonDeviceId(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return deviceId;
        }
        return normalizeDeviceId(deviceId).replace('_', ':');
    }

    /** 判断两个 device_id 是否指向同一台物理设备（兼容 MAC 冒号/下划线/大小写） */
    public static boolean deviceIdsEquivalent(String left, String right) {
        if (StringUtils.isBlank(left) || StringUtils.isBlank(right)) {
            return false;
        }
        if (StringUtils.equals(left, right)) {
            return true;
        }
        return normalizeDeviceId(left).equals(normalizeDeviceId(right));
    }

    /** binding 表 device_id 等价匹配条件 */
    public static Consumer<LambdaQueryWrapper<ParentDeviceBindingEntity>> deviceIdMatch(String deviceId) {
        return w -> {
            if (StringUtils.isBlank(deviceId)) {
                w.apply("1 = 0");
                return;
            }
            String normalized = normalizeDeviceId(deviceId);
            String colonForm = toColonDeviceId(deviceId);
            w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                    .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)
                    .or().eq(ParentDeviceBindingEntity::getDeviceId, colonForm)
                    .or().apply("REPLACE(LOWER(device_id), ':', '_') = {0}", normalized);
        };
    }

    public static ParentDeviceBindingEntity findActiveBinding(
            ParentDeviceBindingDao bindingDao, Long parentUserId, String deviceId) {
        if (parentUserId == null || StringUtils.isBlank(deviceId)) {
            return null;
        }
        return bindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .and(deviceIdMatch(deviceId)));
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
        return bindingDao.selectCount(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .and(deviceIdMatch(deviceId)));
    }

    public static ParentDeviceBindingEntity findPrimaryOwner(
            ParentDeviceBindingDao bindingDao, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        ParentDeviceBindingEntity owner = bindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .eq(ParentDeviceBindingEntity::getRole, ParentDeviceBindingEntity.ROLE_OWNER)
                        .eq(ParentDeviceBindingEntity::getIsPrimary, 1)
                        .and(deviceIdMatch(deviceId))
                        .last("LIMIT 1"));
        if (owner != null) {
            return owner;
        }
        return bindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .eq(ParentDeviceBindingEntity::getRole, ParentDeviceBindingEntity.ROLE_OWNER)
                        .and(deviceIdMatch(deviceId))
                        .orderByDesc(ParentDeviceBindingEntity::getIsPrimary)
                        .last("LIMIT 1"));
    }

    public static ParentDeviceBindingEntity findAnyBinding(
            ParentDeviceBindingDao bindingDao, Long parentUserId, String deviceId) {
        if (parentUserId == null || StringUtils.isBlank(deviceId)) {
            return null;
        }
        return bindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .and(deviceIdMatch(deviceId))
                        .last("LIMIT 1"));
    }

    public static List<ParentDeviceBindingEntity> findActiveBindingsForDevice(
            ParentDeviceBindingDao bindingDao, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return List.of();
        }
        return bindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .and(deviceIdMatch(deviceId)));
    }

    /**
     * Member 绑定 device_id 可能与 Owner 格式不一致时，通过邀请人 Owner 绑定解析真实 deviceId。
     */
    public static ParentDeviceBindingEntity findInviterOwnerBinding(
            ParentDeviceBindingDao bindingDao, ParentDeviceBindingEntity memberBinding) {
        if (bindingDao == null || memberBinding == null || memberBinding.getInvitedBy() == null) {
            return null;
        }
        List<ParentDeviceBindingEntity> inviterBindings = bindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, memberBinding.getInvitedBy())
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .eq(ParentDeviceBindingEntity::getRole, ParentDeviceBindingEntity.ROLE_OWNER));
        if (inviterBindings.isEmpty()) {
            return null;
        }
        for (ParentDeviceBindingEntity ownerBinding : inviterBindings) {
            if (deviceIdsEquivalent(ownerBinding.getDeviceId(), memberBinding.getDeviceId())) {
                return ownerBinding;
            }
        }
        if (inviterBindings.size() == 1) {
            return inviterBindings.get(0);
        }
        return null;
    }

    /** 兼容 device_id 存 MAC 冒号/下划线两种格式 */
    public static DeviceChildEntity findDeviceChild(DeviceChildDao deviceChildDao, String deviceId) {
        if (deviceChildDao == null || StringUtils.isBlank(deviceId)) {
            return null;
        }
        String normalized = normalizeDeviceId(deviceId);
        String colonForm = toColonDeviceId(deviceId);
        return deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>()
                        .and(w -> w.eq(DeviceChildEntity::getDeviceId, deviceId)
                                .or().eq(DeviceChildEntity::getDeviceId, normalized)
                                .or().eq(DeviceChildEntity::getDeviceId, colonForm)
                                .or().apply("REPLACE(LOWER(device_id), ':', '_') = {0}", normalized))
                        .last("LIMIT 1"));
    }

    /** 新建/恢复绑定时写入风险通知默认值：Owner=1，Member=0 */
    public static void applyRiskNotifyDefaults(ParentDeviceBindingEntity binding) {
        if (binding == null) {
            return;
        }
        if (isOwner(binding)) {
            binding.setReceiveRiskNotify(1);
        } else if (binding.getReceiveRiskNotify() == null) {
            binding.setReceiveRiskNotify(0);
        }
    }

    /** outbox 投递：Owner 始终接收；Member 看 receive_risk_notify */
    public static boolean shouldReceiveRiskNotify(ParentDeviceBindingEntity binding) {
        if (binding == null) {
            return false;
        }
        if (isOwner(binding)) {
            return true;
        }
        return binding.getReceiveRiskNotify() != null && binding.getReceiveRiskNotify() == 1;
    }

    public static boolean isReceiveRiskNotifyEnabled(ParentDeviceBindingEntity binding) {
        return shouldReceiveRiskNotify(binding);
    }
}
