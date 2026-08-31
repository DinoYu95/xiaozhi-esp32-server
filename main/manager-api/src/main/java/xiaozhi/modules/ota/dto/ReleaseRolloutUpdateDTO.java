package xiaozhi.modules.ota.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "调整 active 发布灰度比例")
public class ReleaseRolloutUpdateDTO {

    @NotNull
    @Min(1)
    @Max(100)
    @Schema(description = "灰度比例 1-100")
    private Integer rolloutPercent;
}
