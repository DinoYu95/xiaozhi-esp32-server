package xiaozhi.modules.agent.dto;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 智能体数据传输对象
 * 用于在服务层和控制器层之间传递智能体相关的数据
 */
@Data
@Schema(description = "智能体对象")
public class AgentDTO {
    @Schema(description = "智能体编码", example = "AGT_1234567890")
    private String id;

    @Schema(description = "智能体名称", example = "客服助手")
    private String agentName;

    @Schema(description = "语音合成模型名称", example = "tts_model_01")
    private String ttsModelName;

    @Schema(description = "音色名称", example = "voice_01")
    private String ttsVoiceName;

    @Schema(description = "大语言模型名称", example = "llm_model_01")
    private String llmModelName;

    @Schema(description = "视觉模型名称", example = "vllm_model_01")
    private String vllmModelName;

    @Schema(description = "记忆模型ID", example = "mem_model_01")
    private String memModelId;

    @Schema(description = "聊天记录配置：0不记录 1仅文本 2文本+语音", example = "2")
    private Integer chatHistoryConf;

    @Schema(description = "角色设定参数", example = "你是一个专业的客服助手，负责回答用户问题并提供帮助")
    private String systemPrompt;

    @Schema(description = "总结记忆", example = "构建可生长的动态记忆网络，在有限空间内保留关键信息的同时，智能维护信息演变轨迹\n" +
            "根据对话记录，总结user的重要信息，以便在未来的对话中提供更个性化的服务", required = false)
    private String summaryMemory;

    @Schema(description = "最后连接时间", example = "2024-03-20 10:00:00")
    private Date lastConnectedAt;

    @Schema(description = "设备数量", example = "10")
    private Integer deviceCount;

    @Schema(description = "是否已绑定家长（存在 active 的 Owner 绑定）")
    private Boolean parentActivated;

    @Schema(description = "Owner 家长用户 ID")
    private Long ownerParentId;

    @Schema(description = "Owner 家长昵称")
    private String ownerParentNickname;

    @Schema(description = "Owner 绑定的设备 ID（MAC）")
    private String ownerDeviceId;

    @Schema(description = "该设备 active 家长成员总数")
    private Integer boundParentCount;
}