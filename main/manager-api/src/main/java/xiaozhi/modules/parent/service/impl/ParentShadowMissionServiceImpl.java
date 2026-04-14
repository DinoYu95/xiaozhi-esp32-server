package xiaozhi.modules.parent.service.impl;

import java.util.ArrayList;
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
    /** 同一孩子并发的 active 影子任务上限 */
    private static final int MAX_ACTIVE_SHADOW = 5;

    private final ParentShadowMissionDao parentShadowMissionDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;

    @Override
    public List<ParentShadowMissionActiveVO> listActive(String deviceId, Long childId) {
        if (childId == null || deviceChildDao.selectById(childId) == null) {
            return List.of();
        }
        Date now = new Date();
        List<ParentShadowMissionEntity> rows = parentShadowMissionDao.selectList(
                new LambdaQueryWrapper<ParentShadowMissionEntity>()
                        .eq(ParentShadowMissionEntity::getChildId, childId)
                        .eq(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_ACTIVE)
                        .orderByAsc(ParentShadowMissionEntity::getPriority)
                        .orderByAsc(ParentShadowMissionEntity::getId));
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ParentShadowMissionActiveVO> out = new ArrayList<>();
        for (ParentShadowMissionEntity e : rows) {
            if (e.getEndsAt() != null && e.getEndsAt().before(now)) {
                ParentShadowMissionEntity patch = new ParentShadowMissionEntity();
                patch.setId(e.getId());
                patch.setStatus(ParentShadowMissionEntity.STATUS_EXPIRED);
                parentShadowMissionDao.updateById(patch);
                continue;
            }
            out.add(toActiveVO(e));
        }
        return out;
    }

    @Override
    public ParentShadowMissionActiveVO getActive(String deviceId, Long childId) {
        List<ParentShadowMissionActiveVO> list = listActive(deviceId, childId);
        return list.isEmpty() ? null : list.get(0);
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

        long activeCount = parentShadowMissionDao.selectCount(
                new LambdaQueryWrapper<ParentShadowMissionEntity>()
                        .eq(ParentShadowMissionEntity::getChildId, childId)
                        .eq(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_ACTIVE));
        if (activeCount >= MAX_ACTIVE_SHADOW) {
            throw new RenException("进行中影子任务最多" + MAX_ACTIVE_SHADOW + "条，请先让孩子完成部分任务或由家长取消后再添加");
        }

        int nextPriority = 0;
        List<ParentShadowMissionEntity> top = parentShadowMissionDao.selectList(
                new LambdaQueryWrapper<ParentShadowMissionEntity>()
                        .eq(ParentShadowMissionEntity::getChildId, childId)
                        .eq(ParentShadowMissionEntity::getStatus, ParentShadowMissionEntity.STATUS_ACTIVE)
                        .orderByDesc(ParentShadowMissionEntity::getPriority)
                        .orderByDesc(ParentShadowMissionEntity::getId)
                        .last("LIMIT 1"));
        if (top != null && !top.isEmpty() && top.get(0).getPriority() != null) {
            nextPriority = top.get(0).getPriority() + 1;
        }

        Date endsAt = addMinutes(new Date(), dm);

        ParentShadowMissionEntity entity = new ParentShadowMissionEntity();
        entity.setDeviceId(deviceId);
        entity.setChildId(childId);
        entity.setParentUserId(parentUserId);
        entity.setTitle(t);
        entity.setInstructions(ins);
        entity.setEndsAt(endsAt);
        entity.setStatus(ParentShadowMissionEntity.STATUS_ACTIVE);
        entity.setPriority(nextPriority);
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

    @Override
    public void completeByChild(Long childId, Long missionId) {
        if (childId == null || missionId == null) {
            throw new RenException("childId、missionId 必填");
        }
        ParentShadowMissionEntity e = parentShadowMissionDao.selectById(missionId);
        if (e == null) {
            throw new RenException("影子任务不存在");
        }
        if (!childId.equals(e.getChildId())) {
            throw new RenException("任务与当前孩子不匹配");
        }
        if (!ParentShadowMissionEntity.STATUS_ACTIVE.equals(e.getStatus())) {
            throw new RenException("任务已不是进行中状态");
        }
        Date now = new Date();
        if (e.getEndsAt() != null && e.getEndsAt().before(now)) {
            ParentShadowMissionEntity patch = new ParentShadowMissionEntity();
            patch.setId(e.getId());
            patch.setStatus(ParentShadowMissionEntity.STATUS_EXPIRED);
            parentShadowMissionDao.updateById(patch);
            throw new RenException("任务已过期");
        }
        ParentShadowMissionEntity patch = new ParentShadowMissionEntity();
        patch.setId(e.getId());
        patch.setStatus(ParentShadowMissionEntity.STATUS_COMPLETED);
        patch.setUpdateTime(now);
        parentShadowMissionDao.updateById(patch);
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
        int pri = e.getPriority() != null ? e.getPriority() : 0;
        return new ParentShadowMissionActiveVO(
                e.getId(),
                e.getTitle(),
                e.getInstructions(),
                e.getEndsAt(),
                pri);
    }
}
