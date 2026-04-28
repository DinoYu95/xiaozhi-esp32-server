package xiaozhi.modules.risk.vo;

import java.util.Date;

import lombok.Data;

@Data
public class ChildRiskEventAdminVO {
    private Long id;
    private String deviceId;
    private Long childId;
    private String sessionId;
    private Integer riskLevel;
    private String category;
    private String source;
    private String reasonPublic;
    private String status;
    private String suppressedReason;
    private Date createTime;
}
