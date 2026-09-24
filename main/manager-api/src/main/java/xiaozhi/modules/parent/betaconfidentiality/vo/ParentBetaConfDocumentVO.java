package xiaozhi.modules.parent.betaconfidentiality.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测保密协议正文")
public class ParentBetaConfDocumentVO {

    private String version;
    private String title;
    private String summary;
    private String content;
    private Date publishedAt;
}
