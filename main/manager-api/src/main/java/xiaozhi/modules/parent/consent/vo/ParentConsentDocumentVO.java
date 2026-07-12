package xiaozhi.modules.parent.consent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "协议正文（当前 published）")
public class ParentConsentDocumentVO {

    private String version;
    private String title;
    private String summary;
    private String content;
    private Date publishedAt;
}
