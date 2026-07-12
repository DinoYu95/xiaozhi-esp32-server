package xiaozhi.modules.parent.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "同意协议")
public class ParentConsentAgreeDTO {

    @NotBlank
    @Schema(description = "当前 published 版本号")
    private String version;
}
