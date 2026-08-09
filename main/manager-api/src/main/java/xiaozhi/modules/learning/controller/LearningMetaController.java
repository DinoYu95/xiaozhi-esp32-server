package xiaozhi.modules.learning.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestParam;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.learning.entity.KgGraphReleaseEntity;
import xiaozhi.modules.learning.service.LearningKgService;
import xiaozhi.modules.learning.util.LearningChildProfileUtil;
import xiaozhi.modules.learning.util.LearningProfileConstants;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.util.ParentChildAccessHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import xiaozhi.modules.learning.dao.KgNodeRevisionDao;
import xiaozhi.modules.learning.entity.KgNodeRevisionEntity;

@RestController
@RequestMapping("parent-api/learning/meta")
@RequiredArgsConstructor
@Tag(name = "家长端-学习元数据")
public class LearningMetaController {

    private final LearningKgService learningKgService;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final KgNodeRevisionDao kgNodeRevisionDao;

    @GetMapping("/profile-options")
    @Operation(summary = "孩子档案下拉：省/教材（与教研后台一致）")
    public Result<Map<String, Object>> profileOptions() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("provinces", LearningProfileConstants.PROVINCE_OPTIONS);
        body.put(
                "textbooks",
                LearningProfileConstants.TEXTBOOKS.entrySet().stream()
                        .map(e -> Map.of("code", e.getKey(), "label", e.getValue()))
                        .collect(Collectors.toList()));
        body.put("defaults", Map.of(
                "provinceCode", LearningProfileConstants.DEFAULT_PROVINCE,
                "textbookEdition", LearningProfileConstants.DEFAULT_TEXTBOOK));
        return new Result<Map<String, Object>>().ok(body);
    }

    @GetMapping("/graph-match-preview")
    @Operation(summary = "排查：孩子档案与已发布图谱是否匹配（运维/联调）")
    public Result<Map<String, Object>> graphMatchPreview(
            @RequestParam Long childId,
            @RequestParam(required = false, defaultValue = "math") String subject,
            @RequestParam(required = false) Integer grade) {
        Long parentUserId = ParentContext.getParentUserId();
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
                "childProfile",
                Map.of(
                        "currentGrade", child.getCurrentGrade(),
                        "provinceCode", child.getProvinceCode(),
                        "textbookEdition", child.getTextbookEdition(),
                        "textbookSeries", child.getTextbookSeries(),
                        "resolvedProvince", LearningChildProfileUtil.resolveProvince(child),
                        "resolvedTextbook", LearningChildProfileUtil.resolveTextbook(child)));
        int g = LearningChildProfileUtil.clampGraphGrade(child, grade);
        out.put("graphGrade", g);
        try {
            KgGraphReleaseEntity release = learningKgService.findActivePublishedRelease(
                    LearningChildProfileUtil.resolveSubject(subject),
                    LearningChildProfileUtil.resolveProvince(child),
                    LearningChildProfileUtil.resolveTextbook(child),
                    g);
            if (release == null) {
                out.put("matched", false);
                out.put("hint", "kg_graph_release 中无 published 的 math 图谱，请确认教研审核已通过");
                return new Result<Map<String, Object>>().ok(out);
            }
            long skillCount = kgNodeRevisionDao.selectCount(
                    new LambdaQueryWrapper<KgNodeRevisionEntity>()
                            .eq(KgNodeRevisionEntity::getGraphReleaseId, release.getId())
                            .eq(KgNodeRevisionEntity::getGrade, g));
            out.put("matched", true);
            out.put(
                    "release",
                    Map.of(
                            "id", release.getId(),
                            "versionLabel", release.getVersionLabel(),
                            "provinceCode", release.getProvinceCode(),
                            "textbookEdition", release.getTextbookEdition(),
                            "gradeMin", release.getGradeMin(),
                            "gradeMax", release.getGradeMax(),
                            "skillCountAtGrade", skillCount));
            if (skillCount == 0) {
                out.put("hint", "已匹配 release 但该年级无 SKILL 节点，请检查教研发布内容");
            }
        } catch (Exception e) {
            out.put("matched", false);
            out.put("hint", e.getMessage());
        }
        return new Result<Map<String, Object>>().ok(out);
    }
}
