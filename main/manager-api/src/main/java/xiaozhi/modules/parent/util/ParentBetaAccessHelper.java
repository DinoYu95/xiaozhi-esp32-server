package xiaozhi.modules.parent.util;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import xiaozhi.modules.parent.beta.dao.BetaMissionUserStateDao;
import xiaozhi.modules.parent.beta.entity.BetaMissionUserStateEntity;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;

/**
 * 内测资格与家庭共享：本人 {@code is_beta_tester=1}，或与任一内测家长共用同一台设备（active binding）。
 */
public final class ParentBetaAccessHelper {

    private ParentBetaAccessHelper() {
    }

    public static boolean isDirectBetaTester(ParentUserDao userDao, Long parentUserId) {
        if (userDao == null || parentUserId == null) {
            return false;
        }
        ParentUserEntity user = userDao.selectById(parentUserId);
        return user != null && user.getIsBetaTester() != null && user.getIsBetaTester() == 1;
    }

    /** 是否可使用内测反馈 / 内测任务（含家庭共享继承） */
    public static boolean hasBetaAccess(
            ParentUserDao userDao, ParentDeviceBindingDao bindingDao, Long parentUserId) {
        if (isDirectBetaTester(userDao, parentUserId)) {
            return true;
        }
        return hasBetaTesterOnSharedDevice(userDao, bindingDao, parentUserId);
    }

    /** 是否通过家庭共享获得内测资格（本人无 is_beta_tester 标记） */
    public static boolean hasBetaAccessViaSharing(
            ParentUserDao userDao, ParentDeviceBindingDao bindingDao, Long parentUserId) {
        if (isDirectBetaTester(userDao, parentUserId)) {
            return false;
        }
        return hasBetaTesterOnSharedDevice(userDao, bindingDao, parentUserId);
    }

    /** 本人绑定设备上所有 active 家长（含本人），用于设备级步骤共享校验 */
    public static Set<Long> resolveDeviceCohortParentIds(
            ParentDeviceBindingDao bindingDao, Long parentUserId) {
        Set<Long> cohort = new LinkedHashSet<>();
        if (parentUserId != null) {
            cohort.add(parentUserId);
        }
        if (bindingDao == null || parentUserId == null) {
            return cohort;
        }
        List<ParentDeviceBindingEntity> myBindings = bindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE));
        Set<String> seenDevices = new HashSet<>();
        for (ParentDeviceBindingEntity mine : myBindings) {
            String deviceKey = deviceKey(mine.getDeviceId());
            if (deviceKey == null || !seenDevices.add(deviceKey)) {
                continue;
            }
            for (ParentDeviceBindingEntity peer : ParentDeviceAccessHelper.findActiveBindingsForDevice(
                    bindingDao, mine.getDeviceId())) {
                if (peer.getParentUserId() != null) {
                    cohort.add(peer.getParentUserId());
                }
            }
        }
        return cohort;
    }

    /**
     * 被邀请成员首次进入任务时，继承同住设备 Owner（或同设备其他内测家长）已锁定的体验孩子。
     */
    public static Long findHouseholdContextChildId(
            ParentDeviceBindingDao bindingDao,
            BetaMissionUserStateDao betaMissionUserStateDao,
            ParentUserDao userDao,
            Long parentUserId) {
        if (bindingDao == null || betaMissionUserStateDao == null || parentUserId == null) {
            return null;
        }
        List<ParentDeviceBindingEntity> myBindings = bindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE));
        Set<String> seenDevices = new HashSet<>();
        for (ParentDeviceBindingEntity mine : myBindings) {
            String deviceKey = deviceKey(mine.getDeviceId());
            if (deviceKey == null || !seenDevices.add(deviceKey)) {
                continue;
            }
            ParentDeviceBindingEntity owner = ParentDeviceAccessHelper.findPrimaryOwner(
                    bindingDao, mine.getDeviceId());
            Long fromOwner = lockedContextChildId(betaMissionUserStateDao, owner != null ? owner.getParentUserId() : null);
            if (fromOwner != null) {
                return fromOwner;
            }
            for (ParentDeviceBindingEntity peer : ParentDeviceAccessHelper.findActiveBindingsForDevice(
                    bindingDao, mine.getDeviceId())) {
                if (!isDirectBetaTester(userDao, peer.getParentUserId())) {
                    continue;
                }
                Long ctx = lockedContextChildId(betaMissionUserStateDao, peer.getParentUserId());
                if (ctx != null) {
                    return ctx;
                }
            }
        }
        return null;
    }

    private static boolean hasBetaTesterOnSharedDevice(
            ParentUserDao userDao, ParentDeviceBindingDao bindingDao, Long parentUserId) {
        if (userDao == null || bindingDao == null || parentUserId == null) {
            return false;
        }
        List<ParentDeviceBindingEntity> myBindings = bindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE));
        if (myBindings.isEmpty()) {
            return false;
        }
        Set<String> seenDevices = new HashSet<>();
        for (ParentDeviceBindingEntity mine : myBindings) {
            String deviceKey = deviceKey(mine.getDeviceId());
            if (deviceKey == null || !seenDevices.add(deviceKey)) {
                continue;
            }
            for (ParentDeviceBindingEntity peer : ParentDeviceAccessHelper.findActiveBindingsForDevice(
                    bindingDao, mine.getDeviceId())) {
                if (isDirectBetaTester(userDao, peer.getParentUserId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Long lockedContextChildId(BetaMissionUserStateDao dao, Long parentUserId) {
        if (dao == null || parentUserId == null) {
            return null;
        }
        BetaMissionUserStateEntity state = dao.selectOne(
                new LambdaQueryWrapper<BetaMissionUserStateEntity>()
                        .eq(BetaMissionUserStateEntity::getParentUserId, parentUserId));
        if (state == null || state.getContextChildId() == null) {
            return null;
        }
        return state.getContextChildId();
    }

    private static String deviceKey(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        return ParentDeviceAccessHelper.normalizeDeviceId(deviceId);
    }
}
