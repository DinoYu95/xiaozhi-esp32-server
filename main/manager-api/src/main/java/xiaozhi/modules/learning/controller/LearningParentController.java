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
import xiaozhi.modules.learning.service.LearningMasteryService;
import xiaozhi.modules.learning.service.LearningSessionService;
import xiaozhi.modules.learning.vo.LearningMasteryMapVO;
import xiaozhi.modules.learning.vo.LearningModulePathVO;
import xiaozhi.modules.learning.vo.LearningOverviewVO;
import xiaozhi.modules.learning.vo.LearningSkillDetailVO;
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
    private final LearningMasteryService learningMasteryService;

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

    @GetMapping({"/mastery-map", "/mastery/map"})
    @Operation(summary = "掌握地图（按年级模块聚合 SKILL + 掌握度）")
    public Result<LearningMasteryMapVO> masteryMap(
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId,
            @Parameter(description = "学科，默认 math") @RequestParam(required = false) String subject,
            @Parameter(description = "年级 1-6，默认孩子档案 currentGrade，未填则 1") @RequestParam(required = false) Integer grade,
            @Parameter(description = "本周周一 yyyy-MM-dd，与 overview 一致；默认当前自然周") @RequestParam(required = false) String weekStart) {
        return new Result<LearningMasteryMapVO>().ok(
                learningMasteryService.masteryMap(
                        requireParentUserId(), childId, subject, grade, weekStart));
    }

    @GetMapping("/skills/{code}")
    @Operation(summary = "知识点详情（掌握度、前后置、易错点）")
    public Result<LearningSkillDetailVO> skillDetail(
            @PathVariable("code") String code,
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId) {
        return new Result<LearningSkillDetailVO>().ok(
                learningMasteryService.skillDetail(requireParentUserId(), childId, code));
    }

    @GetMapping("/mastery-map/module-path")
    @Operation(summary = "模块内学习顺序（PREREQUISITE_OF 拓扑序）")
    public Result<LearningModulePathVO> modulePath(
            @Parameter(description = "device_child.id", required = true) @RequestParam Long childId,
            @Parameter(description = "模块键，如 ADD、SUB", required = true) @RequestParam String moduleKey,
            @Parameter(description = "学科，默认 math") @RequestParam(required = false) String subject,
            @Parameter(description = "年级，默认档案或 1") @RequestParam(required = false) Integer grade) {
        return new Result<LearningModulePathVO>().ok(
                learningMasteryService.modulePath(
                        requireParentUserId(), childId, subject, grade, moduleKey));
    }
}
