package xiaozhi.modules.zhiban.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智伴 LLM profile 运行时（含解析后的 OpenAI 兼容参数）")
public class ZhibanAgentLlmProfileRuntimeVO {

    private String llmModelId;
    private String model;
    private String apiBase;
    private String apiKey;
    private Double temperature;
    private Integer maxTokens;
}
