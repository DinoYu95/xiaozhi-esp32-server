package xiaozhi.modules.parent.betaconfidentiality.vo;

import java.util.Date;

import lombok.Data;

@Data
public class ParentBetaConfAdminOverviewVO {

    private Boolean enabled;
    private String currentVersion;
    private String title;
    private String summary;
    private String content;
    private Date publishedAt;
    private Integer parentUserTotal;
    private Integer agreedCurrentCount;
    private Integer pendingCount;
}
