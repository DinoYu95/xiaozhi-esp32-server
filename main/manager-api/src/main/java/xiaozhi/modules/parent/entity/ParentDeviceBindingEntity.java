package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 家长-设备绑定
 */
@Data
@TableName("parent_device_binding")
public class ParentDeviceBindingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 家长用户 id */
    private Long parentUserId;
    /** 设备标识（mac） */
    private String deviceId;
    /** 绑定时间 */
    private Date bindTime;
    /** 绑定来源：code/qrcode */
    private String bindSource;
    /** 创建时间 */
    private Date createTime;
}
