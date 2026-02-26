package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 家长端登录 token
 */
@Data
@TableName("parent_user_token")
public class ParentUserTokenEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 家长用户 id */
    private Long parentUserId;
    /** token */
    private String token;
    /** 过期时间 */
    private Date expireTime;
    /** 渠道 */
    private String channel;
    /** 创建时间 */
    private Date createTime;
}
