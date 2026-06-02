package xiaozhi.modules.parent.vo;

import java.util.Date;

import lombok.Data;

@Data
public class ParentRiskWatchVO {
    private Long id;
    private Long parentUserId;
    private Long childId;
    private String watchType;
    private String riskDomain;
    private String riskDomainName;
    private String name;
    private String description;
    private String triggerHint;
    private String pattern;
    private Integer riskLevel;
    private String category;
    private String instructions;
    private String allowedCategories;
    private String status;
    private String statusLabel;
    private String rejectReason;
    private Boolean editable;
    private Date createTime;
    private Date updateTime;
}
