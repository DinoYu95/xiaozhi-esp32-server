package xiaozhi.modules.parent.consent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "智控台-协议概览")
public class ParentConsentAdminOverviewVO {

    private Boolean enabled;
    private String deviceBlockMode;
    private String deviceBlockedPrompt;
    private Integer retentionDaysDisplay;
    private String currentVersion;
    private String title;
    private String summary;
    private String content;
    private Date publishedAt;
    private Integer agreedCurrentCount;
    private Integer parentUserTotal;
    private Integer pendingCount;
}
