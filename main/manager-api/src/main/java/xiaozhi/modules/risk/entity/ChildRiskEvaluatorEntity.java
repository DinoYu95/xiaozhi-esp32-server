package xiaozhi.modules.risk.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("child_risk_evaluator")
public class ChildRiskEvaluatorEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String riskDomain;
    private Integer version;
    /** 0 禁用 1 启用 */
    private Integer status;
    private String modelName;
    private BigDecimal temperature;
    private Integer timeoutMs;
    private String instructions;
    /** JSON 数组字符串 */
    private String allowedCategories;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
}
