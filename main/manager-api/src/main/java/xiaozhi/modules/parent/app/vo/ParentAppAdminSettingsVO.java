package xiaozhi.modules.parent.app.vo;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智控台-小程序审核配置")
public class ParentAppAdminSettingsVO {

    private String accessMode;
    private Boolean reviewMode;
    private String appName;
    private String homeBannerTitle;
    private String homeBannerSubtitle;
    private List<String> homeBannerTags;
    private String loginHint;
    private Map<String, String> guestTabHints;
}
