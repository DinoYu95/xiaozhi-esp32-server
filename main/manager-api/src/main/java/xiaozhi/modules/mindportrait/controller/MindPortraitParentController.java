package xiaozhi.modules.mindportrait.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.mindportrait.service.MindPortraitService;
import xiaozhi.modules.mindportrait.vo.MindGraphVO;
import xiaozhi.modules.mindportrait.vo.MindNotificationPageVO;
import xiaozhi.modules.mindportrait.vo.MindWeeklyDigestVO;
import xiaozhi.modules.mindportrait.vo.MindWellnessSummaryVO;
import xiaozhi.modules.parent.context.ParentContext;

@RestController
@RequestMapping("/parent-api/mind-portrait")
@RequiredArgsConstructor
@Tag(name = "家长端-心绪陪伴")
public class MindPortraitParentController {

    private final MindPortraitService mindPortraitService;

    private static Long requireParentUserId() {
        Long id = ParentContext.getParentUserId();
        if (id == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return id;
    }

    @GetMapping("/wellness-summary")
    @Operation(summary = "心绪陪伴概览（机器人 Tab + 详情页）")
    public Result<MindWellnessSummaryVO> wellnessSummary(
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId) {
        return new Result<MindWellnessSummaryVO>().ok(
                mindPortraitService.getWellnessSummary(requireParentUserId(), childId));
    }

    @GetMapping("/graph")
    @Operation(summary = "心绪图谱渲染数据（内部/教研，家长端 UI 已下线）")
    public Result<MindGraphVO> graph(
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId) {
        return new Result<MindGraphVO>().ok(
                mindPortraitService.getGraph(requireParentUserId(), childId));
    }

    @GetMapping("/notifications")
    @Operation(summary = "成长亮点通知列表")
    public Result<MindNotificationPageVO> notifications(
            @RequestParam Long childId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return new Result<MindNotificationPageVO>().ok(
                mindPortraitService.listNotifications(requireParentUserId(), childId, page, pageSize));
    }

    @PostMapping("/notifications/{id}/read")
    @Operation(summary = "标记通知已读")
    public Result<Void> markRead(@PathVariable("id") Long id) {
        mindPortraitService.markNotificationRead(requireParentUserId(), id);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/weekly-digest")
    @Operation(summary = "心绪陪伴周报（会话卡片消息）")
    public Result<MindWeeklyDigestVO> weeklyDigest(
            @RequestParam Long childId,
            @RequestParam(required = false) String weekStart) {
        return new Result<MindWeeklyDigestVO>().ok(
                mindPortraitService.weeklyDigest(requireParentUserId(), childId, weekStart));
    }

    @PostMapping("/settings")
    @Operation(summary = "更新家长通知偏好")
    public Result<Void> updateSettings(@RequestBody MindSettingsBody body) {
        mindPortraitService.updateSettings(
                requireParentUserId(), body.getChildId(),
                body.getInstantNotifyEnabled(), body.getWeeklyDigestEnabled());
        return new Result<Void>().ok(null);
    }

    @Data
    public static class MindSettingsBody {
        private Long childId;
        private Boolean instantNotifyEnabled;
        private Boolean weeklyDigestEnabled;
    }
}
