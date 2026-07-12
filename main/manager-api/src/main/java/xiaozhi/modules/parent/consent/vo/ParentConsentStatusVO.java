package xiaozhi.modules.parent.consent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "协议签署状态")
public class ParentConsentStatusVO {

    private Boolean consentEnabled;
    private Boolean consentRequired;
    private Boolean blocking;
    private String currentVersion;
    private String agreedVersion;
    private Date agreedAt;
    private String title;
    private String summary;
    private String deviceBlockMode;
}
