package xiaozhi.modules.llm.service;

import xiaozhi.modules.llm.dto.LlmOpenAiCallConfig;

/**
 * LLM服务接口
 * 支持多种大模型调用
 */
public interface LLMService {

    /**
     * 生成聊天记录总结
     * 
     * @param conversation   对话内容
     * @param promptTemplate 提示词模板
     * @return 总结结果
     */
    String generateSummary(String conversation, String promptTemplate);

    /**
     * 生成聊天记录总结（使用默认提示词）
     * 
     * @param conversation 对话内容
     * @return 总结结果
     */
    String generateSummary(String conversation);

    /**
     * 生成聊天记录总结（指定模型ID）
     * 
     * @param conversation 对话内容
     * @param modelId      模型ID
     * @return 总结结果
     */
    String generateSummaryWithModel(String conversation, String modelId);

    /**
     * 生成聊天记录总结（指定模型ID和提示词模板）
     * 
     * @param conversation   对话内容
     * @param promptTemplate 提示词模板
     * @param modelId        模型ID
     * @return 总结结果
     */
    String generateSummary(String conversation, String promptTemplate, String modelId);

    /**
     * 生成聊天记录总结（包含历史记忆合并）
     * 
     * @param conversation   对话内容
     * @param historyMemory  历史记忆
     * @param promptTemplate 提示词模板
     * @param modelId        模型ID
     * @return 总结结果
     */
    String generateSummaryWithHistory(String conversation, String historyMemory, String promptTemplate, String modelId);

    /**
     * 检查服务是否可用
     * 
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 检查指定模型的服务是否可用
     * 
     * @param modelId 模型ID
     * @return 是否可用
     */
    boolean isAvailable(String modelId);

    /**
     * 使用参数字典等提供的 OpenAI 兼容直连配置调用（prompt 作为 user 消息全文）
     */
    String chatWithOpenAiConfig(String prompt, LlmOpenAiCallConfig config);

    /** 直连配置是否具备 baseUrl + apiKey */
    boolean isInlineConfigAvailable(LlmOpenAiCallConfig config);
}