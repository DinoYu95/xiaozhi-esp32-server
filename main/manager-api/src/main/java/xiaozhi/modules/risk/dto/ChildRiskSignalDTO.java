package xiaozhi.modules.risk.dto;

import lombok.Data;

@Data
public class ChildRiskSignalDTO {
    private Long childId;
    private String deviceId;
    private String sessionId;
    /** 1 最严重 3 最轻 */
    private Integer riskLevel;
    private String category;
    private Boolean needAlert;
    private String source;
    private String reasonPublic;
}
