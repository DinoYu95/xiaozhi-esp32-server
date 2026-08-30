package xiaozhi.modules.ota.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "设备端 OTA 检查")
public class DeviceOtaCheckReqDTO {

    @NotBlank
    private String macAddress;

    private String board;

    private String deviceType;

    private String systemVersion;

    private String appVersion;

    private String otaChannel;
}
