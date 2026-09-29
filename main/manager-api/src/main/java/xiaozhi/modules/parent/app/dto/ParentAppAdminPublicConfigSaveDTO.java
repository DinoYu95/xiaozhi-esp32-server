package xiaozhi.modules.parent.app.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "小程序公开文案配置")
public class ParentAppAdminPublicConfigSaveDTO {

    private String appName;

    @NotBlank
    private String homeBannerTitle;

    @NotBlank
    private String homeBannerSubtitle;

    private List<String> homeBannerTags;
    private String loginHint;
    private Map<String, String> guestTabHints;
}
