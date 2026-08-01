package xiaozhi.modules.learning.controller;

import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.learning.service.LearningKgService;
import xiaozhi.modules.learning.vo.KgReleaseVO;

@RestController
@RequestMapping("admin/learning/kg")
@RequiredArgsConstructor
@Tag(name = "管理端-学习知识图谱")
public class LearningKgAdminController {

    private final LearningKgService learningKgService;

    @PostMapping("/release")
    @Operation(summary = "创建 draft 图谱版本")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Long> createRelease(
            @RequestParam String versionLabel,
            @RequestParam(defaultValue = "math") String subject,
            @RequestParam(defaultValue = "1") int gradeMin,
            @RequestParam(defaultValue = "3") int gradeMax) {
        return new Result<Long>().ok(learningKgService.createDraftRelease(versionLabel, subject, gradeMin, gradeMax));
    }

    @PostMapping("/release/{releaseId}/import-nodes")
    @Operation(summary = "导入 nodes.csv")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> importNodes(@PathVariable Long releaseId, @RequestParam("file") MultipartFile file)
            throws Exception {
        try (InputStream in = file.getInputStream()) {
            learningKgService.importNodesCsv(releaseId, in);
        }
        return new Result<Void>().ok(null);
    }

    @PostMapping("/release/{releaseId}/import-edges")
    @Operation(summary = "导入 edges.csv")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> importEdges(@PathVariable Long releaseId, @RequestParam("file") MultipartFile file)
            throws Exception {
        try (InputStream in = file.getInputStream()) {
            learningKgService.importEdgesCsv(releaseId, in);
        }
        return new Result<Void>().ok(null);
    }

    @PostMapping("/release/{releaseId}/validate")
    @Operation(summary = "校验 draft")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> validate(@PathVariable Long releaseId) {
        learningKgService.validateRelease(releaseId);
        return new Result<Void>().ok(null);
    }

    @PostMapping("/release/{releaseId}/publish")
    @Operation(summary = "发布并重建 closure")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<Void> publish(@PathVariable Long releaseId) {
        learningKgService.publishRelease(releaseId);
        return new Result<Void>().ok(null);
    }

    @GetMapping("/release/active")
    @Operation(summary = "当前 published 版本")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<KgReleaseVO> active(@RequestParam(defaultValue = "math") String subject) {
        KgReleaseVO vo = learningKgService.getActivePublishedRelease(StringUtils.defaultIfBlank(subject, "math"));
        return new Result<KgReleaseVO>().ok(vo);
    }
}
