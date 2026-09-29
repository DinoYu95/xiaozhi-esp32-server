package xiaozhi.modules.parent.app.vo;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "小程序游客公开配置")
public class ParentAppPublicConfigVO {

    private String accessMode;
    private String appName;
    private HomeBanner homeBanner;
    private String loginHint;
    private Map<String, String> guestTabHints;
    private Boolean reviewMode;
    private String updatedAt;

    @Data
    public static class HomeBanner {
        private String title;
        private String subtitle;
        private List<String> tags;
    }
}
