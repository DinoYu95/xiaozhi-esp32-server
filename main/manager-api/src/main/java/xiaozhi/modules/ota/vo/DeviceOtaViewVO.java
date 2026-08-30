package xiaozhi.modules.ota.vo;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "DevOps 设备 OTA 视图")
public class DeviceOtaViewVO {

    private String deviceId;
    private String macAddress;
    private String board;
    private String deviceType;
    private String systemVersion;
    private String appVersion;
    private String otaChannel;
    private Boolean online;
    private Boolean autoUpdate;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Date lastConnectedAt;

    private String parentDisplayName;
    private Map<String, String> latestVisible = new LinkedHashMap<>();
    private Map<String, Boolean> updateAvailable = new LinkedHashMap<>();
}
