package xiaozhi.modules.parent.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.dao.ParentProfileReminderDao;
import xiaozhi.modules.parent.entity.ParentProfileReminderEntity;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.util.ParentChildAccessHelper;

@RestController
@RequestMapping("parent-api/profile-reminders")
@RequiredArgsConstructor
@Tag(name = "家长端-孩子档案提醒")
public class ParentProfileReminderController {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ParentProfileReminderDao reminderDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;

    @GetMapping
    @Operation(summary = "拉取当前有效档案提醒（query childId）")
    public Result<List<ProfileReminderVO>> listActive(@org.springframework.web.bind.annotation.RequestParam Long childId) {
        Long parentUserId = ParentContext.getParentUserId();
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        LocalDate today = LocalDate.now(ZONE);
        List<ParentProfileReminderEntity> list = reminderDao.selectList(
                new LambdaQueryWrapper<ParentProfileReminderEntity>()
                        .eq(ParentProfileReminderEntity::getChildId, childId)
                        .eq(ParentProfileReminderEntity::getRemindDate, today)
                        .isNull(ParentProfileReminderEntity::getDismissedAt)
                        .orderByDesc(ParentProfileReminderEntity::getId));
        return new Result<List<ProfileReminderVO>>()
                .ok(list.stream().map(ParentProfileReminderController::toVo).collect(Collectors.toList()));
    }

    @PostMapping("/{reminderId}/dismiss")
    @Operation(summary = "关闭提醒")
    public Result<Void> dismiss(@PathVariable Long reminderId) {
        Long parentUserId = ParentContext.getParentUserId();
        ParentProfileReminderEntity r = reminderDao.selectById(reminderId);
        if (r == null) {
            return new Result<Void>().ok(null);
        }
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, r.getChildId());
        r.setDismissedAt(new Date());
        reminderDao.updateById(r);
        return new Result<Void>().ok(null);
    }

    private static ProfileReminderVO toVo(ParentProfileReminderEntity e) {
        ProfileReminderVO vo = new ProfileReminderVO();
        vo.setId(e.getId());
        vo.setReminderType(e.getReminderType());
        vo.setTitle(e.getTitle());
        vo.setBody(e.getBody());
        vo.setAction(e.getAction());
        vo.setRemindDate(e.getRemindDate() != null ? e.getRemindDate().toString() : null);
        vo.setPromotionDate(e.getPromotionDate() != null ? e.getPromotionDate().toString() : null);
        return vo;
    }

    @Data
    public static class ProfileReminderVO {
        private Long id;
        private String reminderType;
        private String title;
        private String body;
        private String action;
        private String remindDate;
        private String promotionDate;
    }
}
