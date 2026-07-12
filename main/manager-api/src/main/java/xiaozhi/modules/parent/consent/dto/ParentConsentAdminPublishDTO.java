package xiaozhi.modules.parent.consent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "智控台发布协议新版本")
public class ParentConsentAdminPublishDTO {

    @NotBlank
    private String title;

    @NotBlank
    private String summary;

    @NotBlank
    private String content;
}
