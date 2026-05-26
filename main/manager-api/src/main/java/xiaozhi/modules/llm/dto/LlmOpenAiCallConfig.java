package xiaozhi.modules.llm.dto;

import lombok.Data;

/**
 * OpenAI 兼容接口直连参数（不经过 ai_model_config）
 */
@Data
public class LlmOpenAiCallConfig {

    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Double temperature;
    private Integer maxTokens;

    public boolean isComplete() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
