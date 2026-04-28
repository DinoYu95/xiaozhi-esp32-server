package xiaozhi.modules.risk.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.risk.service.ChildRiskService;
import xiaozhi.modules.risk.vo.ParentRiskNotificationPageVO;

@RestController
@RequestMapping("/parent-api/risk-alerts")
@RequiredArgsConstructor
@Tag(name = "家长端-风险提示通知")
public class ParentRiskAlertController {

    private final ChildRiskService childRiskService;

    private Long requirePid() {
        Long id = ParentContext.getParentUserId();
        if (id == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return id;
    }

    @GetMapping
    @Operation(summary = "分页通知列表")
    public Result<ParentRiskNotificationPageVO> page(
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long pid = requirePid();
        childRiskService.verifyParentOwnsChild(pid, childId);
        return new Result<ParentRiskNotificationPageVO>()
                .ok(childRiskService.pageNotificationsForParent(pid, childId, page, pageSize));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "未读条数（可仅统计某孩子）")
    public Result<Long> unread(
            @RequestParam(required = false) Long childId) {
        Long pid = requirePid();
        return new Result<Long>().ok(childRiskService.countUnreadForParent(pid, childId));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "标记单条已读")
    public Result<Void> markRead(@PathVariable("id") Long id) {
        Long pid = requirePid();
        childRiskService.markReadForParent(pid, id);
        return new Result<Void>().ok(null);
    }
}
