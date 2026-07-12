package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_device_binding")
public class ParentDeviceBindingEntity {

    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_MEMBER = "member";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_REMOVED = "removed";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private String deviceId;
    private Date bindTime;
    private String bindSource;
    private String role;
    private Integer isPrimary;
    private Long invitedBy;
    private String status;
    /** 是否接收该设备儿童风险提示；Owner 恒为 1，Member 由 Owner 配置 */
    private Integer receiveRiskNotify;
    private Date createTime;
    private Date updatedAt;
}
