package xiaozhi.modules.learning.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.modules.learning.dao.LearningPromotionScheduleDao;
import xiaozhi.modules.learning.entity.LearningPromotionScheduleEntity;
import xiaozhi.modules.learning.service.LearningPromotionScheduleService;
import xiaozhi.modules.learning.util.LearningProfileConstants;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentProfileReminderDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentProfileReminderEntity;

@Service
@RequiredArgsConstructor
public class LearningPromotionScheduleServiceImpl implements LearningPromotionScheduleService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String TYPE_GRADE_PROMOTION = "GRADE_PROMOTION";

    private final LearningPromotionScheduleDao scheduleDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentProfileReminderDao reminderDao;

    @Override
    public List<LearningPromotionScheduleEntity> listAll() {
        return scheduleDao.selectList(
                new LambdaQueryWrapper<LearningPromotionScheduleEntity>()
                        .orderByAsc(LearningPromotionScheduleEntity::getProvinceCode)
                        .orderByAsc(LearningPromotionScheduleEntity::getSchoolLevel));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdate(LearningPromotionScheduleEntity entity) {
        if (entity.getProvinceCode() == null) {
            entity.setProvinceCode("");
        }
        entity.setProvinceCode(entity.getProvinceCode().trim().toLowerCase());
        if (entity.getSchoolLevel() == null || entity.getSchoolLevel().isBlank()) {
            throw new xiaozhi.common.exception.RenException("schoolLevel 必填");
        }
        entity.setSchoolLevel(entity.getSchoolLevel().trim().toUpperCase());
        entity.setUpdateTime(new Date());
        if (entity.getId() != null) {
            scheduleDao.updateById(entity);
        } else {
            scheduleDao.insert(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void runDailyPromotionReminders() {
        LocalDate today = LocalDate.now(ZONE);
        List<DeviceChildEntity> children = deviceChildDao.selectList(null);
        if (children.isEmpty()) {
            return;
        }
        List<LearningPromotionScheduleEntity> schedules = listAll();
        for (DeviceChildEntity child : children) {
            if (child.getCurrentGrade() == null || child.getCurrentGrade() <= 0) {
                continue;
            }
            String level = LearningProfileConstants.schoolLevelFromGrade(child.getCurrentGrade());
            String province = LearningProfileConstants.normalizeProvince(child.getProvinceCode());
            LearningPromotionScheduleEntity schedule = findSchedule(schedules, province, level);
            if (schedule == null) {
                continue;
            }
            LocalDate promotionDate =
                    safeDate(today.getYear(), schedule.getPromotionMonth(), schedule.getPromotionDay());
            LocalDate remindDate = promotionDate.minusDays(1);
            if (!today.equals(remindDate)) {
                continue;
            }
            upsertReminder(child, remindDate, promotionDate);
        }
    }

    private LearningPromotionScheduleEntity findSchedule(
            List<LearningPromotionScheduleEntity> schedules, String province, String level) {
        LearningPromotionScheduleEntity hit = schedules.stream()
                .filter(s -> level.equalsIgnoreCase(s.getSchoolLevel()))
                .filter(s -> province.equalsIgnoreCase(StringUtils.defaultString(s.getProvinceCode())))
                .findFirst()
                .orElse(null);
        if (hit != null) {
            return hit;
        }
        return schedules.stream()
                .filter(s -> level.equalsIgnoreCase(s.getSchoolLevel()))
                .filter(s -> StringUtils.isBlank(s.getProvinceCode()))
                .findFirst()
                .orElse(null);
    }

    private void upsertReminder(DeviceChildEntity child, LocalDate remindDate, LocalDate promotionDate) {
        ParentProfileReminderEntity existing = reminderDao.selectOne(
                new LambdaQueryWrapper<ParentProfileReminderEntity>()
                        .eq(ParentProfileReminderEntity::getChildId, child.getId())
                        .eq(ParentProfileReminderEntity::getReminderType, TYPE_GRADE_PROMOTION)
                        .eq(ParentProfileReminderEntity::getRemindDate, remindDate)
                        .isNull(ParentProfileReminderEntity::getDismissedAt)
                        .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        ParentProfileReminderEntity r = new ParentProfileReminderEntity();
        r.setChildId(child.getId());
        r.setReminderType(TYPE_GRADE_PROMOTION);
        r.setTitle("升学季提醒：请更新孩子年级");
        r.setBody("明天（"
                + promotionDate
                + "）为系统配置的升学日，请确认「"
                + StringUtils.defaultString(child.getName(), "孩子")
                + "」的年级与教材是否已升级。");
        r.setAction("OPEN_CHILD_PROFILE");
        r.setRemindDate(remindDate);
        r.setPromotionDate(promotionDate);
        r.setCreateTime(new Date());
        reminderDao.insert(r);
    }

    private static LocalDate safeDate(int year, Integer month, Integer day) {
        int m = month != null && month >= 1 && month <= 12 ? month : 8;
        int d = day != null && day >= 1 && day <= 28 ? day : 31;
        try {
            return LocalDate.of(year, m, d);
        } catch (Exception e) {
            return LocalDate.of(year, 8, 31);
        }
    }
}
