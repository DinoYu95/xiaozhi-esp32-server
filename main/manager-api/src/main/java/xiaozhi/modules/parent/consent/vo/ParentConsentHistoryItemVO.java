package xiaozhi.modules.parent.consent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "协议历史版本")
public class ParentConsentHistoryItemVO {

    private String version;
    private String title;
    private String status;
    private Date publishedAt;
    private Date updateTime;
}
