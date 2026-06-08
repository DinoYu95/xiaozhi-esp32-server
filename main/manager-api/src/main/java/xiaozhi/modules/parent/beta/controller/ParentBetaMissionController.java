package xiaozhi.modules.parent.beta.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.beta.dto.BetaMissionContextDTO;
import xiaozhi.modules.parent.beta.service.BetaMissionService;
import xiaozhi.modules.parent.beta.vo.BetaMissionEntryStatusVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionOverviewVO;
import xiaozhi.modules.parent.context.ParentContext;

@RestController
@RequestMapping("/parent-api/beta-mission")
@RequiredArgsConstructor
@Tag(name = "家长端-内测体验任务")
public class ParentBetaMissionController {

    private final BetaMissionService betaMissionService;

    @GetMapping("/entry-status")
    @Operation(summary = "入口状态（始终 200，非内测 showEntry=false）")
    public Result<BetaMissionEntryStatusVO> entryStatus() {
        Long parentUserId = requireParentUserId();
        return new Result<BetaMissionEntryStatusVO>().ok(betaMissionService.getEntryStatus(parentUserId));
    }

    @GetMapping("/overview")
    @Operation(summary = "任务概览")
    public Result<BetaMissionOverviewVO> overview() {
        Long parentUserId = requireParentUserId();
        return new Result<BetaMissionOverviewVO>().ok(betaMissionService.getOverview(parentUserId));
    }

    @PostMapping("/sync")
    @Operation(summary = "同步自动校验步骤，返回与 overview 相同结构")
    public Result<BetaMissionOverviewVO> sync() {
        Long parentUserId = requireParentUserId();
        return new Result<BetaMissionOverviewVO>().ok(betaMissionService.sync(parentUserId));
    }

    @PutMapping("/context")
    @Operation(summary = "锁定体验对象（仅首次）")
    public Result<BetaMissionOverviewVO> setContext(@RequestBody @Valid BetaMissionContextDTO dto) {
        Long parentUserId = requireParentUserId();
        return new Result<BetaMissionOverviewVO>().ok(betaMissionService.setContext(parentUserId, dto));
    }

    @PostMapping("/steps/{stepKey}/skip")
    @Operation(summary = "跳过选做步骤")
    public Result<BetaMissionOverviewVO> skip(@PathVariable String stepKey) {
        Long parentUserId = requireParentUserId();
        return new Result<BetaMissionOverviewVO>().ok(betaMissionService.skipStep(parentUserId, stepKey));
    }

    @PostMapping("/steps/{stepKey}/visit")
    @Operation(summary = "访问标记（如 risk_alert_viewed）")
    public Result<Void> visit(@PathVariable String stepKey) {
        Long parentUserId = requireParentUserId();
        betaMissionService.visitStep(parentUserId, stepKey);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/dismiss-popup")
    @Operation(summary = "关闭首次引导弹窗")
    public Result<Void> dismissPopup() {
        Long parentUserId = requireParentUserId();
        betaMissionService.dismissPopup(parentUserId);
        return new Result<Void>().ok(null);
    }

    private static Long requireParentUserId() {
        Long id = ParentContext.getParentUserId();
        if (id == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return id;
    }
}
