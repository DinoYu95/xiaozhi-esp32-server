package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 家长与助手的聊天记录（按家长+孩子维度，非设备维度）
 */
@Data
@TableName("parent_chat_history")
public class ParentChatHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 家长用户 id */
    private Long parentUserId;

    /** 对话所属孩子 id（device_child.id） */
    private Long childId;

    /** 设备 id（该孩子主设备，用于关联 agent） */
    private String deviceId;

    /** 智能体 id */
    private String agentId;

    /** 会话 id，格式 parent_{parentUserId}_{childId} */
    private String sessionId;

    /** 消息类型：1=家长 2=助手 */
    private Byte chatType;

    /** 文本内容 */
    private String content;

    /** 语音消息对应的音频 id（parent_chat_audio.id），空表示纯文本 */
    private String audioId;

    /** 聊天图片 OSS objectKey（远程看娃等） */
    private String imageObjectKey;

    /** text / snapshot / text_with_snapshot */
    private String messageKind;

    /** 远程看娃请求 id */
    private String snapshotRequestId;

    /** 创建时间 */
    private Date createTime;
}
