package xiaozhi.modules.parent.consent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_consent_record")
public class ParentConsentRecordEntity {

    public static final String CHANNEL_WECHAT_MINIPROGRAM = "wechat_miniprogram";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private String version;
    private Date agreedAt;
    private String channel;
    private String clientIp;
    private String userAgent;
}
