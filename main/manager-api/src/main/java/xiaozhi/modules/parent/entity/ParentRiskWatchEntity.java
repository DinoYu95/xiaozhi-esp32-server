package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_risk_watch")
public class ParentRiskWatchEntity {

    public static final String TYPE_KEYWORD = "KEYWORD";
    public static final String TYPE_EVALUATOR = "EVALUATOR";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ENABLED = "enabled";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_DISABLED = "disabled";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private Long childId;
    private String watchType;
    private String riskDomain;
    private String name;
    private String description;
    private String triggerHint;
    private String pattern;
    private String ruleType;
    private Integer riskLevel;
    private String category;
    private String instructions;
    private String allowedCategories;
    private String status;
    private String auditNote;
    private String rejectReason;
    private Long linkedRuleId;
    private Integer version;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
}
