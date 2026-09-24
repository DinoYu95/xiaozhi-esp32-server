package xiaozhi.modules.parent.betaconfidentiality.vo;

import java.util.Date;

import lombok.Data;

@Data
public class ParentBetaConfHistoryItemVO {

    private String version;
    private String title;
    private String status;
    private Date publishedAt;
    private Date updateTime;
}
