package xiaozhi.modules.parent.consent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "协议正文（当前 published）")
public class ParentConsentDocumentVO {

    /** 与 {@link #currentVersion} 相同，便于与 /status 字段对齐 */
    private String version;
    @Schema(description = "当前 published 版本号（与 version 一致）")
    private String currentVersion;
    private String title;
    private String summary;
    private String content;
    private Date publishedAt;
}
