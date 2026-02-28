package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 家长端登录身份：一种登录方式一条记录，同一家长可有多种方式（微信/手机 × 多渠道）
 */
@Data
@TableName("parent_auth")
public class ParentAuthEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 家长用户 id */
    private Long parentUserId;
    /** 认证类型：wechat/phone */
    private String authType;
    /** 渠道：wechat/mini_program/app */
    private String channel;
    /** 微信 open_id（auth_type=wechat 时必填） */
    private String openId;
    /** 微信 union_id */
    private String unionId;
    /** 手机号加密（auth_type=phone 时必填） */
    private String phone;
    /** 创建时间 */
    private Date createTime;
    /** 更新时间 */
    private Date updateTime;
}
