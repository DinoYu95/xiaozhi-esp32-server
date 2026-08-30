package xiaozhi.modules.ota.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "创建白名单池")
public class WhitelistPoolCreateDTO {

    @NotBlank
    @Size(max = 128)
    private String name;

    private String description;

    private List<String> macAddresses = new ArrayList<>();
}
