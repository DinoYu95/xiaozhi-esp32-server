package xiaozhi.modules.risk.vo;

import lombok.Data;

@Data
public class ChildRiskRulePublicVO {
    private Long id;
    private String name;
    private String ruleType;
    private String pattern;
    private Integer riskLevel;
    private String category;
    /** 管理端列表用；zhiban 拉取可忽略 */
    private Integer status;
    private Integer sortOrder;
}
