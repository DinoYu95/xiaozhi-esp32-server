package xiaozhi.modules.learning.controller;

import java.util.ArrayList;
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
import xiaozhi.modules.learning.service.impl.LearningKgServiceImpl;
import xiaozhi.modules.learning.util.ChildGradeOptionsUtil;
import xiaozhi.modules.learning.util.LearningChildProfileUtil;
import xiaozhi.modules.learning.util.LearningGeoConstants;
import xiaozhi.modules.learning.util.LearningProfileConstants;
import xiaozhi.modules.parent.context.ParentContext;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.util.ParentChildAccessHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import xiaozhi.modules.learning.dao.KgGraphReleaseDao;
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
    private final KgGraphReleaseDao kgGraphReleaseDao;
    private final KgNodeRevisionDao kgNodeRevisionDao;

    @GetMapping("/profile-options")
    @Operation(summary = "孩子档案下拉：省/市/上下册/教材/年级（与教研后台一致）")
    public Result<Map<String, Object>> profileOptions() {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("provinces", LearningGeoConstants.provinces());
            body.put("semesters", LearningGeoConstants.semesters());
            body.put("citiesByProvince", LearningGeoConstants.citiesByProvince());
            body.put(
                    "textbooks",
                    LearningProfileConstants.TEXTBOOKS.entrySet().stream()
                            .map(e -> Map.of("code", e.getKey(), "label", e.getValue()))
                            .collect(Collectors.toList()));
            body.put("grades", ChildGradeOptionsUtil.profileGradeOptions());
            body.put(
                    "defaults",
                    Map.of(
                            "provinceCode", LearningProfileConstants.DEFAULT_PROVINCE,
                            "cityCode", "CN_all",
                            "semester", LearningGeoConstants.SEMESTER_UPPER,
                            "textbookEdition", LearningProfileConstants.DEFAULT_TEXTBOOK,
                            "currentGrade", 1));
            return new Result<Map<String, Object>>().ok(body);
        } catch (Exception e) {
            return new Result<Map<String, Object>>().error("加载省市区选项失败: " + e.getMessage());
        }
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
        String sub = LearningChildProfileUtil.resolveSubject(subject);
        String resolvedProvince = LearningChildProfileUtil.resolveProvince(child);
        String resolvedCity = LearningChildProfileUtil.resolveCity(child);
        String resolvedSemester = LearningChildProfileUtil.resolveSemester(child);
        String resolvedTextbook = LearningChildProfileUtil.resolveTextbook(child);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(
                "childProfile",
                Map.of(
                        "currentGrade", child.getCurrentGrade(),
                        "provinceCode", child.getProvinceCode(),
                        "cityCode", child.getCityCode(),
                        "semester", child.getSemester(),
                        "textbookEdition", child.getTextbookEdition(),
                        "textbookSeries", child.getTextbookSeries(),
                        "resolvedProvince", resolvedProvince,
                        "resolvedCity", resolvedCity,
                        "resolvedSemester", resolvedSemester,
                        "resolvedTextbook", resolvedTextbook));
        int g = LearningChildProfileUtil.clampGraphGrade(child, grade);
        out.put("graphGrade", g);
        out.put(
                "expectedMatch",
                Map.of(
                        "subject", sub,
                        "provinceCode", resolvedProvince,
                        "cityCode", resolvedCity,
                        "textbookEdition", resolvedTextbook,
                        "semester", resolvedSemester,
                        "grade", g));
        try {
            KgGraphReleaseEntity release = learningKgService.findActivePublishedRelease(
                    sub, resolvedProvince, resolvedCity, resolvedTextbook, resolvedSemester, g);
            if (release == null) {
                out.put("matched", false);
                out.put("hint", "未命中任何 published 图谱，请对照 expectedMatch 与 publishedCandidates");
                out.put("publishedCandidates", listPublishedCandidates(sub, resolvedProvince));
                return new Result<Map<String, Object>>().ok(out);
            }
            long skillCount = learningKgService.countSkillNodesAtGrade(release.getId(), g);
            long revisionCount = kgNodeRevisionDao.selectCount(
                    LearningKgServiceImpl.revisionGradeWrapper(release.getId(), release, g));
            out.put("matched", true);
            out.put(
                    "release",
                    Map.of(
                            "id", release.getId(),
                            "versionLabel", release.getVersionLabel(),
                            "provinceCode", release.getProvinceCode(),
                            "cityCode", release.getCityCode(),
                            "semester", release.getSemester(),
                            "textbookEdition", release.getTextbookEdition(),
                            "gradeMin", release.getGradeMin(),
                            "gradeMax", release.getGradeMax(),
                            "skillCountAtGrade", skillCount,
                            "revisionCountAtGrade", revisionCount));
            if (skillCount == 0) {
                out.put(
                        "hint",
                        "已匹配 release 但该年级无 SKILL 节点（revision 行数="
                                + revisionCount
                                + "）。请检查 kg_node.node_type 是否为 SKILL，或 kg_node_revision.grade 是否为 "
                                + g);
            }
        } catch (Exception e) {
            out.put("matched", false);
            out.put("hint", e.getMessage());
        }
        return new Result<Map<String, Object>>().ok(out);
    }

    /** 同省已发布图谱列表，便于对照 city_code / semester / textbook 是否一致 */
    private List<Map<String, Object>> listPublishedCandidates(String subject, String provinceCode) {
        List<KgGraphReleaseEntity> rows =
                kgGraphReleaseDao.selectList(
                        new LambdaQueryWrapper<KgGraphReleaseEntity>()
                                .eq(KgGraphReleaseEntity::getSubject, subject)
                                .eq(KgGraphReleaseEntity::getStatus, KgGraphReleaseEntity.STATUS_PUBLISHED)
                                .and(
                                        w ->
                                                w.eq(KgGraphReleaseEntity::getProvinceCode, provinceCode)
                                                        .or()
                                                        .eq(
                                                                KgGraphReleaseEntity::getProvinceCode,
                                                                LearningProfileConstants.DEFAULT_PROVINCE))
                                .orderByDesc(KgGraphReleaseEntity::getPublishedAt)
                                .last("LIMIT 10"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (KgGraphReleaseEntity r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("versionLabel", r.getVersionLabel());
            m.put("provinceCode", r.getProvinceCode());
            m.put("cityCode", r.getCityCode());
            m.put("semester", r.getSemester());
            m.put("textbookEdition", r.getTextbookEdition());
            m.put("gradeMin", r.getGradeMin());
            m.put("gradeMax", r.getGradeMax());
            out.add(m);
        }
        return out;
    }
}
