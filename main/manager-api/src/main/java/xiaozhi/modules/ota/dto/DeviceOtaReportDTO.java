package xiaozhi.modules.ota.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "设备升级结果上报")
public class DeviceOtaReportDTO {

    @NotBlank
    private String macAddress;

    @NotNull
    private Long releaseId;

    @NotBlank
    @Pattern(regexp = "^(system|app)$")
    private String type;

    private String fromVersion;

    private String toVersion;

    @NotBlank
    @Pattern(regexp = "^(pending|downloading|success|failed|skipped)$")
    private String status;

    private String errorMessage;
}
