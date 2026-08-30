package xiaozhi.modules.ota.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ota_whitelist_pool")
public class OtaWhitelistPoolEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String description;
    private Date createdAt;
    private Date updatedAt;
}
