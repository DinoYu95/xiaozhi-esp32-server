package xiaozhi.modules.ota.vo;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "OTA 发布")
public class ReleaseVO {

    private Long id;
    private String packageId;
    private String type;
    private String hardware;
    private String version;
    private String channel;
    private Integer rolloutPercent;
    private List<Long> whitelistPoolIds = new ArrayList<>();
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Date publishedAt;

    private String publishedBy;
    private ReleaseCoverageVO coverage;
    private Boolean rollbackAvailable;
    private Long previousReleaseId;
}
