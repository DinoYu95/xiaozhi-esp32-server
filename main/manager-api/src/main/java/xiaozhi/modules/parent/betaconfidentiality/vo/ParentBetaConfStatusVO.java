package xiaozhi.modules.parent.betaconfidentiality.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "内测保密协议签署状态")
public class ParentBetaConfStatusVO {

    private Boolean betaConfEnabled;
    private Boolean betaConfRequired;
    private Boolean blocking;
    private String currentVersion;
    private String agreedVersion;
    private Date agreedAt;
    private String title;
    private String summary;
}
