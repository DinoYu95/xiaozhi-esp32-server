package xiaozhi.modules.risk.vo;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** zhiban-agent 拉取 */
@Data
@Schema(description = "儿童风险领域判别器（智伴）")
public class ChildRiskEvaluatorPublicVO {
    private Long id;
    private String code;
    private String name;
    private String riskDomain;
    private Integer version;
    /** 0 禁用 1 启用（管理端列表展示；智伴拉取仅 status=1） */
    private Integer status;
    private String modelName;
    private BigDecimal temperature;
    private Integer timeoutMs;
    private String instructions;
    /** JSON 数组，如 ["self_harm_hint","other"] */
    private String allowedCategories;
    private Integer sortOrder;
}
