package xiaozhi.modules.zhiban.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智伴 LLM profile（引用模型配置 llmModelId）")
public class ZhibanAgentLlmProfileDTO {

    @Schema(description = "ai_model_config.id（LLM 类型）")
    private String llmModelId;

    @Schema(description = "temperature，结构化任务建议 0")
    private Double temperature;

    @Schema(description = "max_tokens，可选")
    private Integer maxTokens;
}
