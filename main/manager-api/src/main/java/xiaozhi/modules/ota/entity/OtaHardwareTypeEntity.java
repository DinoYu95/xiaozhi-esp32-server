package xiaozhi.modules.ota.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ota_hardware_type")
public class OtaHardwareTypeEntity {

    @TableId(value = "hw_key", type = IdType.INPUT)
    private String hwKey;

    private String name;
    private String description;
    private Integer enabled;
    private Date createdAt;
    private Date updatedAt;
}
