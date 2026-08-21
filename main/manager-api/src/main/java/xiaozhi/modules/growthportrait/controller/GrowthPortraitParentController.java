package xiaozhi.modules.growthportrait.controller;

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
import xiaozhi.modules.growthportrait.service.GrowthPortraitService;
import xiaozhi.modules.growthportrait.vo.GrowthGraphVO;
import xiaozhi.modules.growthportrait.vo.GrowthNotificationPageVO;
import xiaozhi.modules.growthportrait.vo.GrowthWeeklyDigestVO;
import xiaozhi.modules.parent.context.ParentContext;

@RestController
@RequestMapping("/parent-api/growth-portrait")
@RequiredArgsConstructor
@Tag(name = "家长端-成长星图")
public class GrowthPortraitParentController {

    private final GrowthPortraitService growthPortraitService;

    private static Long requireParentUserId() {
        Long id = ParentContext.getParentUserId();
        if (id == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return id;
    }

    @GetMapping("/graph")
    @Operation(summary = "成长星图渲染数据（节点+状态+光效）")
    public Result<GrowthGraphVO> graph(
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId) {
        return new Result<GrowthGraphVO>().ok(
                growthPortraitService.getGraph(requireParentUserId(), childId));
    }

    @GetMapping("/notifications")
    @Operation(summary = "成长亮点通知列表")
    public Result<GrowthNotificationPageVO> notifications(
            @RequestParam Long childId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return new Result<GrowthNotificationPageVO>().ok(
                growthPortraitService.listNotifications(requireParentUserId(), childId, page, pageSize));
    }

    @PostMapping("/notifications/{id}/read")
    @Operation(summary = "标记通知已读")
    public Result<Void> markRead(@PathVariable("id") Long id) {
        growthPortraitService.markNotificationRead(requireParentUserId(), id);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/weekly-digest")
    @Operation(summary = "成长星图周报")
    public Result<GrowthWeeklyDigestVO> weeklyDigest(
            @RequestParam Long childId,
            @RequestParam(required = false) String weekStart) {
        return new Result<GrowthWeeklyDigestVO>().ok(
                growthPortraitService.weeklyDigest(requireParentUserId(), childId, weekStart));
    }

    @PostMapping("/settings")
    @Operation(summary = "更新家长通知偏好")
    public Result<Void> updateSettings(@RequestBody GrowthSettingsBody body) {
        growthPortraitService.updateSettings(
                requireParentUserId(), body.getChildId(),
                body.getInstantNotifyEnabled(), body.getWeeklyDigestEnabled());
        return new Result<Void>().ok(null);
    }

    @Data
    public static class GrowthSettingsBody {
        private Long childId;
        private Boolean instantNotifyEnabled;
        private Boolean weeklyDigestEnabled;
    }
}
