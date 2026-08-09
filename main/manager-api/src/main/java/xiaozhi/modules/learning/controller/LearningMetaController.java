package xiaozhi.modules.learning.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.learning.util.LearningProfileConstants;

@RestController
@RequestMapping("parent-api/learning/meta")
@RequiredArgsConstructor
@Tag(name = "家长端-学习元数据")
public class LearningMetaController {

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
}
