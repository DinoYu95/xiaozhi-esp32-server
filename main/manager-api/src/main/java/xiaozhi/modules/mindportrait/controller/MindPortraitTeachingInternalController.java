package xiaozhi.modules.mindportrait.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.mindportrait.dto.TeachingMpPublishDTO;
import xiaozhi.modules.mindportrait.service.MindPortraitService;

@RestController
@RequestMapping("internal/teaching/mind")
@RequiredArgsConstructor
@Tag(name = "内部-心绪图谱发布")
public class MindPortraitTeachingInternalController {

    private final MindPortraitService mindPortraitService;

    @Value("${teaching.internal-api-key:}")
    private String internalApiKey;

    @PostMapping("/publish")
    @Operation(summary = "教研审批通过后发布心绪图谱模板")
    public Result<Long> publish(
            @RequestHeader(value = "X-Teaching-Internal-Key", required = false) String key,
            @RequestBody TeachingMpPublishDTO body) {
        String expected = StringUtils.trimToEmpty(internalApiKey);
        String got = StringUtils.trimToEmpty(key);
        if (StringUtils.isBlank(expected)) {
            throw new RenException("服务端未配置 TEACHING_INTERNAL_API_KEY");
        }
        if (!expected.equals(got)) {
            throw new RenException("无效的内部密钥");
        }
        return new Result<Long>().ok(mindPortraitService.publishFromTeaching(body));
    }
}
