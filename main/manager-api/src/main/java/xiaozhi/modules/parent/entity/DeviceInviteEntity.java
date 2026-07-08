package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("device_invite")
public class DeviceInviteEntity {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_REVOKED = "revoked";
    public static final String STATUS_EXHAUSTED = "exhausted";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private Long inviterParentId;
    private String tokenHash;
    private Date expiresAt;
    private Integer maxUses;
    private Integer usedCount;
    private String status;
    private Date createdAt;
    private Date revokedAt;
}
