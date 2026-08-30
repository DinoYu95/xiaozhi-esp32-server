package xiaozhi.modules.ota.vo;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "OTA 包")
public class PackageVO {

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

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Date createdAt;
}
