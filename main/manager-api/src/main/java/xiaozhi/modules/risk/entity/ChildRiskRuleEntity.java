package xiaozhi.modules.risk.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("child_risk_rule")
public class ChildRiskRuleEntity {

    public static final String TYPE_KEYWORD = "KEYWORD";
    public static final String TYPE_REGEX = "REGEX";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String ruleType;
    private String pattern;
    /** 1 最严重 */
    private Integer riskLevel;
    private String category;
    /** PLATFORM 平台红线；PARENT 家长家庭观察词 */
    private String ruleScope;
    private Long parentUserId;
    private Long childId;
    private Integer sortOrder;
    /** 0 禁用 1 启用 */
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
