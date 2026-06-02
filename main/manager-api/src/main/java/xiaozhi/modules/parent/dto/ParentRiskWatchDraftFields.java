package xiaozhi.modules.parent.dto;

import lombok.Data;

@Data
public class ParentRiskWatchDraftFields {
    private String name;
    private String description;
    private String triggerHint;
    private String riskDomain;
    private String pattern;
    private Integer riskLevel;
    private String category;
    private String instructions;
    private String allowedCategories;
}
