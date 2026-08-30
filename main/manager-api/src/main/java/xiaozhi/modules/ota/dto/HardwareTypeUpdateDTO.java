package xiaozhi.modules.ota.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "更新硬件类型")
public class HardwareTypeUpdateDTO {

    @Size(max = 128)
    private String name;

    private String description;

    private Boolean enabled;
}
