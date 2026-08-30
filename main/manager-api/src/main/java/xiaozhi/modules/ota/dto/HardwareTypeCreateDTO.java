package xiaozhi.modules.ota.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "创建硬件类型")
public class HardwareTypeCreateDTO {

    @NotBlank
    @Size(max = 64)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "key 仅允许字母数字下划线与短横线")
    private String key;

    @NotBlank
    @Size(max = 128)
    private String name;

    private String description;
}
