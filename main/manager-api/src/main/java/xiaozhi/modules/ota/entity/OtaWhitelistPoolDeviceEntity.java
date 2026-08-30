package xiaozhi.modules.ota.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ota_whitelist_pool_device")
public class OtaWhitelistPoolDeviceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long poolId;
    private String macAddress;
}
