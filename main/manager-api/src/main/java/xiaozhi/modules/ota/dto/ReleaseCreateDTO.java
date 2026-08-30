package xiaozhi.modules.ota.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "创建 OTA 发布")
public class ReleaseCreateDTO {

    @NotBlank
    private String packageId;

    @NotBlank
    @Pattern(regexp = "^(stable|beta)$")
    private String channel;

    @Min(1)
    @Max(100)
    private Integer rolloutPercent = 100;

    private List<Long> whitelistPoolIds = new ArrayList<>();

    private List<String> extraMacAddresses = new ArrayList<>();
}
