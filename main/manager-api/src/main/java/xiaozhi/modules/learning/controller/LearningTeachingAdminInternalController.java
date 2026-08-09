package xiaozhi.modules.learning.controller;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
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
import xiaozhi.modules.learning.entity.LearningPromotionScheduleEntity;
import xiaozhi.modules.learning.service.LearningPromotionScheduleService;

@RestController
@RequestMapping("internal/teaching/admin")
@RequiredArgsConstructor
@Tag(name = "内部-教研后台管理")
public class LearningTeachingAdminInternalController {

    private final LearningPromotionScheduleService promotionScheduleService;

    @Value("${teaching.internal-api-key:}")
    private String internalApiKey;

    @GetMapping("/promotion-schedules")
    @Operation(summary = "升学日期配置列表")
    public Result<List<LearningPromotionScheduleEntity>> listPromotionSchedules(
            @RequestHeader(value = "X-Teaching-Internal-Key", required = false) String key) {
        assertKey(key);
        return new Result<List<LearningPromotionScheduleEntity>>().ok(promotionScheduleService.listAll());
    }

    @PostMapping("/promotion-schedules")
    @Operation(summary = "保存升学日期（provinceCode 空串=全局默认）")
    public Result<Void> savePromotionSchedule(
            @RequestHeader(value = "X-Teaching-Internal-Key", required = false) String key,
            @RequestBody LearningPromotionScheduleEntity body) {
        assertKey(key);
        promotionScheduleService.saveOrUpdate(body);
        return new Result<Void>().ok(null);
    }

    private void assertKey(String key) {
        String expected = StringUtils.trimToEmpty(internalApiKey);
        if (StringUtils.isBlank(expected) || !expected.equals(StringUtils.trimToEmpty(key))) {
            throw new RenException("无效的内部密钥");
        }
    }
}
