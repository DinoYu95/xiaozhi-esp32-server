package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "远程监控开播响应")
public class ParentLiveStartVO {

    private Long sessionId;
    private String sessionNo;
    private String status;
    private String deviceId;
    private String playUrl;
    private String playUrlHls;
    private String mode;
    private Integer maxDurationSec;
    private Integer heartbeatIntervalSec;
    private String message;
}
