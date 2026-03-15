package xiaozhi.modules.parent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 家长聊天语音消息的音频存储
 */
@Data
@TableName("parent_chat_audio")
public class ParentChatAudioEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 家长用户 id（上传者） */
    private Long parentUserId;

    /** 音频数据 */
    private byte[] audio;
}
