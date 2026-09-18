package xiaozhi.modules.zhiban.vo;

import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xiaozhi.modules.zhiban.dto.ZhibanAgentConfigDTO;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "zhiban-agent 拉取的运行时配置（含 LLM 解析结果）")
public class ZhibanAgentRuntimeVO extends ZhibanAgentConfigDTO {

    private Map<String, ZhibanAgentLlmProfileRuntimeVO> resolvedLlmProfiles = new LinkedHashMap<>();

    private ZhibanAgentLlmProfileRuntimeVO resolvedEmbedding;
}
