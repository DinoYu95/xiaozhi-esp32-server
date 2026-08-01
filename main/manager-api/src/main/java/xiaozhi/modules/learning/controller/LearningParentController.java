package xiaozhi.modules.learning.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
import xiaozhi.modules.learning.service.LearningSessionService;
import xiaozhi.modules.learning.vo.LearningOverviewVO;
import xiaozhi.modules.learning.vo.LearningSessionDetailVO;
import xiaozhi.modules.learning.vo.LearningSessionPageVO;
import xiaozhi.modules.learning.vo.LearningWeeklyDigestVO;
import xiaozhi.modules.parent.context.ParentContext;

@RestController
@RequestMapping("/parent-api/learning")
@RequiredArgsConstructor
@Tag(name = "家长端-学习洞察")
public class LearningParentController {

    private final LearningSessionService learningSessionService;

    private static Long requireParentUserId() {
        Long id = ParentContext.getParentUserId();
        if (id == null) {
            throw new RenException(ErrorCode.PARENT_TOKEN_INVALID);
        }
        return id;
    }

    @GetMapping("/overview")
    @Operation(summary = "学习洞察首页（档案状态 + 当周周报）")
    public Result<LearningOverviewVO> overview(
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId,
            @Parameter(description = "周起始 yyyy-MM-dd，默认本周一") @RequestParam(required = false) String weekStart) {
        return new Result<LearningOverviewVO>().ok(
                learningSessionService.overview(requireParentUserId(), childId, StringUtils.trimToNull(weekStart)));
    }

    @GetMapping("/weekly-digest")
    @Operation(summary = "学习周报")
    public Result<LearningWeeklyDigestVO> weeklyDigest(
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId,
            @Parameter(description = "周起始 yyyy-MM-dd，默认本周一") @RequestParam(required = false) String weekStart) {
        return new Result<LearningWeeklyDigestVO>().ok(
                learningSessionService.weeklyDigest(
                        requireParentUserId(), childId, StringUtils.trimToNull(weekStart)));
    }

    @GetMapping("/sessions")
    @Operation(summary = "作业辅导 session 分页（按周）")
    public Result<LearningSessionPageVO> sessions(
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId,
            @Parameter(description = "周起始 yyyy-MM-dd，默认本周一") @RequestParam(required = false) String weekStart,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return new Result<LearningSessionPageVO>().ok(
                learningSessionService.pageSessions(
                        requireParentUserId(), childId, StringUtils.trimToNull(weekStart), page, pageSize));
    }

    @GetMapping("/sessions/{sessionId}")
    @Operation(summary = "作业辅导 session 详情")
    public Result<LearningSessionDetailVO> sessionDetail(
            @PathVariable("sessionId") Long sessionId) {
        return new Result<LearningSessionDetailVO>().ok(
                learningSessionService.getSessionDetail(requireParentUserId(), sessionId));
    }
}
