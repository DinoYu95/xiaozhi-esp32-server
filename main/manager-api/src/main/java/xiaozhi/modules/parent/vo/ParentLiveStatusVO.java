package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "远程监控会话状态")
public class ParentLiveStatusVO {

    private Long sessionId;
    private String sessionNo;
    private String status;
    private String deviceId;
    private String playUrl;
    private String playUrlHls;
    private String mode;
    private String failCode;
    private String failMessage;
    private Integer elapsedSec;
    private Integer remainingSec;
    private Integer maxDurationSec;
    private Integer heartbeatIntervalSec;
}
