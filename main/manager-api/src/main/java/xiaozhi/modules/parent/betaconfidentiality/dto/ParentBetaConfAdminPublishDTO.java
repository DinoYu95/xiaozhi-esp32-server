package xiaozhi.modules.parent.betaconfidentiality.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "智控台发布内测保密协议新版本")
public class ParentBetaConfAdminPublishDTO {

    @NotBlank
    private String title;

    @NotBlank
    private String summary;

    @NotBlank
    private String content;
}
