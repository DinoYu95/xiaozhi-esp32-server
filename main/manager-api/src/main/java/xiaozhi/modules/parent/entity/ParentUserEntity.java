package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 家长端用户（与 sys_user 隔离）
 */
@Data
@TableName("parent_user")
public class ParentUserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 微信 open_id */
    private String openId;
    /** 微信 union_id */
    private String unionId;
    /** 手机号（加密存储） */
    private String phone;
    /** 昵称 */
    private String nickname;
    /** 头像 URL */
    private String avatarUrl;
    /** 渠道：wechat/mini_program/app */
    private String channel;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
}
