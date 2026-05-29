package xiaozhi.modules.parent.vo;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "家长端反馈详情")
public class ParentFeedbackDetailVO extends ParentFeedbackVO {

    @Schema(description = "自动采集上下文")
    private Map<String, Object> contextSnapshot;
}
