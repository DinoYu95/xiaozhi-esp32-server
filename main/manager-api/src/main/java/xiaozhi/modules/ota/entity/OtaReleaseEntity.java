package xiaozhi.modules.ota.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ota_release")
public class OtaReleaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String packageId;
    private String channel;
    private Integer rolloutPercent;
    private String status;
    private Long previousReleaseId;
    private String extraMacAddresses;
    private String publishedBy;
    private Date publishedAt;
}
