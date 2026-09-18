package xiaozhi.modules.zhiban.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智伴 Agent 运行配置（存 sys_params server.zhiban_agent_config）")
public class ZhibanAgentConfigDTO {

    private Integer version = 1;

    @Schema(description = "router/chat/vision/extract/parentTools/childRiskJudge")
    private Map<String, ZhibanAgentLlmProfileDTO> llmProfiles = new LinkedHashMap<>();

    @Schema(description = "向量 embedding（llmModelId 指向 embedding 模型配置）")
    private ZhibanAgentLlmProfileDTO embedding = new ZhibanAgentLlmProfileDTO();

    private ZhibanAgentIntegrationDTO integration = new ZhibanAgentIntegrationDTO();

    private ZhibanAgentMemoryDTO memory = new ZhibanAgentMemoryDTO();

    private ZhibanAgentFeaturesDTO features = new ZhibanAgentFeaturesDTO();

    @Data
    public static class ZhibanAgentIntegrationDTO {
        private String xiaozhiServerUrl = "http://xiaozhi-server:8003";
        private Integer mcpCallTimeoutSec = 45;
        private Integer photoMcpTimeoutSec = 60;
    }

    @Data
    public static class ZhibanAgentMemoryDTO {
        private Boolean routerFastPath = true;
        private Integer loadContextMaxChars = 1200;
        private Boolean episodicSkipIfEmpty = true;
        private Boolean blockAgentIdentity = true;
        private Integer homeworkEpisodicTtlDays = 30;
        private Boolean profileEnabled = true;
        private Boolean summaryEnabled = true;
    }

    @Data
    public static class ZhibanAgentFeaturesDTO {
        private Boolean homeworkPhotoJudgeEnabled = true;
        private Boolean childRiskLlmJudgeEnabled = false;
        private Integer childRiskLlmEveryN = 1;
    }
}
