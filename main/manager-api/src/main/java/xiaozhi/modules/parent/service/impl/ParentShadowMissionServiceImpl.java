package xiaozhi.modules.parent.service.impl;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentShadowMissionDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentShadowMissionEntity;
import xiaozhi.modules.parent.service.ParentShadowMissionService;
import xiaozhi.modules.parent.vo.ParentShadowMissionActiveVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionUpsertResultVO;

@Service
@RequiredArgsConstructor
public class ParentShadowMissionServiceImpl implements ParentShadowMissionService {

    private static final int TITLE_MAX = 128;
    private static final int INSTRUCTIONS_MAX = 2000;
    private static final int DURATION_MIN = 5;
    private static final int DURATION_MAX = 180;

    private final ParentShadowMissionDao parentShadowMissionDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;

    @Override
    public ParentShadowMissionActiveVO getActive(String deviceId, Long childId) {
        if (StringUtils.isBlank(deviceId) || childId == null) {
            return null;
        }
        Date now = new Date();
        List<ParentShadowMissionEntity> candidates = parentShadowMissionDao.selectList(
                new LambdaQueryWrapper<ParentShadowMissionEntity>()
                        .eq(ParentShadowMissionEntity::getDeviceId, deviceId)
                        .eq(ParentShadowMissionEntity::getChildId, childId)
                        .eq(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_ACTIVE)
                        .orderByDesc(ParentShadowMissionEntity::getId)
                        .last("LIMIT 5"));
        if (candidates == null || candidates.isEmpty()) {
            return tryNormalizedDevice(deviceId, childId, now);
        }
        for (ParentShadowMissionEntity e : candidates) {
            if (e.getEndsAt() != null && e.getEndsAt().before(now)) {
                ParentShadowMissionEntity patch = new ParentShadowMissionEntity();
                patch.setId(e.getId());
                patch.setStatus(ParentShadowMissionEntity.STATUS_EXPIRED);
                parentShadowMissionDao.updateById(patch);
                continue;
            }
            return toActiveVO(e);
        }
        return null;
    }

    private ParentShadowMissionActiveVO tryNormalizedDevice(String deviceId, Long childId, Date now) {
        String norm = deviceId.replace(":", "_").toLowerCase();
        if (norm.equals(deviceId)) {
            return null;
        }
        List<ParentShadowMissionEntity> candidates = parentShadowMissionDao.selectList(
                new LambdaQueryWrapper<ParentShadowMissionEntity>()
                        .eq(ParentShadowMissionEntity::getDeviceId, norm)
                        .eq(ParentShadowMissionEntity::getChildId, childId)
                        .eq(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_ACTIVE)
                        .orderByDesc(ParentShadowMissionEntity::getId)
                        .last("LIMIT 5"));
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (ParentShadowMissionEntity e : candidates) {
            if (e.getEndsAt() != null && e.getEndsAt().before(now)) {
                ParentShadowMissionEntity patch = new ParentShadowMissionEntity();
                patch.setId(e.getId());
                patch.setStatus(ParentShadowMissionEntity.STATUS_EXPIRED);
                parentShadowMissionDao.updateById(patch);
                continue;
            }
            return toActiveVO(e);
        }
        return null;
    }

    @Override
    public ParentShadowMissionUpsertResultVO upsert(
            Long parentUserId,
            Long childId,
            String title,
            String instructions,
            int durationMinutes) {
        if (parentUserId == null || childId == null) {
            throw new RenException("parentUserId、childId 必填");
        }
        String t = StringUtils.trimToEmpty(title);
        String ins = StringUtils.trimToEmpty(instructions);
        if (StringUtils.isBlank(t) || StringUtils.isBlank(ins)) {
            throw new RenException("title、instructions 必填");
        }
        if (t.length() > TITLE_MAX) {
            throw new RenException("标题不超过" + TITLE_MAX + "字");
        }
        if (ins.length() > INSTRUCTIONS_MAX) {
            throw new RenException("说明不超过" + INSTRUCTIONS_MAX + "字");
        }
        int dm = durationMinutes;
        if (dm < DURATION_MIN) {
            dm = DURATION_MIN;
        }
        if (dm > DURATION_MAX) {
            dm = DURATION_MAX;
        }

        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        String deviceId = child.getDeviceId();
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ensureParentCanAccessChild(parentUserId, deviceId);

        Date endsAt = addMinutes(new Date(), dm);
        cancelActiveRows(deviceId, childId);

        ParentShadowMissionEntity entity = new ParentShadowMissionEntity();
        entity.setDeviceId(deviceId);
        entity.setChildId(childId);
        entity.setParentUserId(parentUserId);
        entity.setTitle(t);
        entity.setInstructions(ins);
        entity.setEndsAt(endsAt);
        entity.setStatus(ParentShadowMissionEntity.STATUS_ACTIVE);
        Date now = new Date();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        parentShadowMissionDao.insert(entity);
        return new ParentShadowMissionUpsertResultVO(entity.getId(), entity.getTitle());
    }

    @Override
    public void cancel(Long parentUserId, Long childId) {
        if (parentUserId == null || childId == null) {
            throw new RenException("parentUserId、childId 必填");
        }
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        String deviceId = child.getDeviceId();
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ensureParentCanAccessChild(parentUserId, deviceId);
        parentShadowMissionDao.update(null,
                new LambdaUpdateWrapper<ParentShadowMissionEntity>()
                        .eq(ParentShadowMissionEntity::getDeviceId, deviceId)
                        .eq(ParentShadowMissionEntity::getChildId, childId)
                        .eq(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_ACTIVE)
                        .set(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_CANCELLED));
    }

    private void cancelActiveRows(String deviceId, Long childId) {
        parentShadowMissionDao.update(null,
                new LambdaUpdateWrapper<ParentShadowMissionEntity>()
                        .eq(ParentShadowMissionEntity::getDeviceId, deviceId)
                        .eq(ParentShadowMissionEntity::getChildId, childId)
                        .eq(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_ACTIVE)
                        .set(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_CANCELLED));
    }

    private void ensureParentCanAccessChild(Long parentUserId, String deviceId) {
        String normalized = deviceId.replace(":", "_").toLowerCase();
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
    }

    private static Date addMinutes(Date from, int minutes) {
        Calendar c = Calendar.getInstance();
        c.setTime(from);
        c.add(Calendar.MINUTE, minutes);
        return c.getTime();
    }

    private static ParentShadowMissionActiveVO toActiveVO(ParentShadowMissionEntity e) {
        return new ParentShadowMissionActiveVO(
                e.getId(),
                e.getTitle(),
                e.getInstructions(),
                e.getEndsAt());
    }
}
