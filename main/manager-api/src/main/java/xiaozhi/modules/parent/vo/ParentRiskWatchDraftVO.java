package xiaozhi.modules.parent.vo;

import lombok.Data;

@Data
public class ParentRiskWatchDraftVO {
    private String watchType;
    private String riskDomain;
    private String name;
    private String description;
    private String triggerHint;
    private String pattern;
    private Integer riskLevel;
    private String category;
    private String instructions;
    private String allowedCategories;
}
