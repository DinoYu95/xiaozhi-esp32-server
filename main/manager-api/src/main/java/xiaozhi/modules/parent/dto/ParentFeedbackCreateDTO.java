package xiaozhi.modules.parent.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "提交内测反馈")
public class ParentFeedbackCreateDTO {

    @NotBlank
    @Schema(description = "问题类型：device_bind/child_voiceprint/chat_voice/skill/shadow_mission/other", requiredMode = Schema.RequiredMode.REQUIRED)
    private String category;

    @NotBlank
    @Size(max = 2000)
    @Schema(description = "问题描述", requiredMode = Schema.RequiredMode.REQUIRED)
    private String description;

    @Schema(description = "是否阻塞使用")
    private Boolean blocking;

    @Schema(description = "是否允许运营联系")
    private Boolean allowContact;

    @Schema(description = "小程序自动采集的上下文（JSON 对象）")
    private Map<String, Object> contextSnapshot;

    @Schema(description = "截图 URL 列表（先调上传接口，最多 3 条）")
    private List<String> imageUrls;
}
