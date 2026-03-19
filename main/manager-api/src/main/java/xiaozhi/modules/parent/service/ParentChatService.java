package xiaozhi.modules.parent.service;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.web.multipart.MultipartFile;

import xiaozhi.modules.parent.dto.ParentChatSendDTO;
import xiaozhi.modules.parent.vo.ParentChatHistoryPageVO;
import xiaozhi.modules.parent.vo.ParentChatMessageVO;

/**
 * 家长端聊天服务（陪伴页：家长↔孩子的专属小助手）
 */
public interface ParentChatService {

    /**
     * 上传家长语音，返回 audioId 供发送消息时使用
     */
    String uploadAudio(Long parentUserId, Long childId, MultipartFile file);

    /**
     * 发送消息并获取助手回复。content 必填；audioId 可选，若有则保存时关联供回放
     */
    ParentChatMessageVO send(Long parentUserId, ParentChatSendDTO dto);

    /**
     * 获取家长与某孩子的聊天历史（全量，兼容旧逻辑）
     */
    List<ParentChatMessageVO> getHistory(Long parentUserId, Long childId);

    /**
     * 获取家长与某孩子的聊天历史（分页，按 createTime 降序，page=1 为最新）
     */
    ParentChatHistoryPageVO getHistoryPage(Long parentUserId, Long childId, int page, int pageSize);

    /**
     * 获取语音播放 token（一次性）
     */
    String getPlayToken(Long parentUserId, String audioId);

    /**
     * 按 token 取出并消费音频
     */
    byte[] getAudioByPlayToken(String token);
}
