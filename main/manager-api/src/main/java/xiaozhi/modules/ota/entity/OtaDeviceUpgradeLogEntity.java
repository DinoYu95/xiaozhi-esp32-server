package xiaozhi.modules.ota.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ota_device_upgrade_log")
public class OtaDeviceUpgradeLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long releaseId;
    private String macAddress;
    private String pkgType;
    private String fromVersion;
    private String toVersion;
    private String status;
    private String errorMessage;
    private Date reportedAt;
}
