package xiaozhi.modules.growthportrait.controller;

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
import xiaozhi.modules.growthportrait.dto.TeachingGpPublishDTO;
import xiaozhi.modules.growthportrait.service.GrowthPortraitService;

@RestController
@RequestMapping("internal/teaching/growth")
@RequiredArgsConstructor
@Tag(name = "内部-成长星图发布")
public class GrowthPortraitTeachingInternalController {

    private final GrowthPortraitService growthPortraitService;

    @Value("${teaching.internal-api-key:}")
    private String internalApiKey;

    @PostMapping("/publish")
    @Operation(summary = "教研审批通过后发布成长星图模板")
    public Result<Long> publish(
            @RequestHeader(value = "X-Teaching-Internal-Key", required = false) String key,
            @RequestBody TeachingGpPublishDTO body) {
        String expected = StringUtils.trimToEmpty(internalApiKey);
        String got = StringUtils.trimToEmpty(key);
        if (StringUtils.isBlank(expected)) {
            throw new RenException("服务端未配置 TEACHING_INTERNAL_API_KEY");
        }
        if (!expected.equals(got)) {
            throw new RenException("无效的内部密钥");
        }
        return new Result<Long>().ok(growthPortraitService.publishFromTeaching(body));
    }
}
