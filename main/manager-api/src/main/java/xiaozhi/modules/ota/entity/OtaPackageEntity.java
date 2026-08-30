package xiaozhi.modules.ota.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ota_package")
public class OtaPackageEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String type;
    private String hardware;
    private String version;
    private String channel;
    private String filename;
    private String ossKey;
    private Long sizeBytes;
    private String sha256;
    private String status;
    private String notes;
    private String createdBy;
    private Date createdAt;
}
