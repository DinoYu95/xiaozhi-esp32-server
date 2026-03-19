package xiaozhi.modules.parent.controller;

import org.springframework.web.bind.annotation.GetMapping;
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
import xiaozhi.modules.parent.service.DailyBriefService;
import xiaozhi.modules.parent.vo.DailyBriefVO;

/**
 * 家长端主孩子今日简报
 */
@RestController
@RequestMapping("/parent-api/daily-brief")
@RequiredArgsConstructor
@Tag(name = "家长端-今日简报")
public class ParentDailyBriefController {

    private final DailyBriefService dailyBriefService;

    @GetMapping
    @Operation(summary = "获取主孩子的今日简报")
    public Result<DailyBriefVO> getDailyBrief(
            @Parameter(description = "孩子ID（device_child.id）", required = true) @RequestParam Long childId) {
        Long parentUserId = ParentContext.getParentUserId();
        if (parentUserId == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        DailyBriefVO vo = dailyBriefService.getDailyBrief(parentUserId, childId);
        return new Result<DailyBriefVO>().ok(vo);
    }
}
