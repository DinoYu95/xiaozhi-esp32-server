package xiaozhi.modules.risk.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChildRiskEvaluatorSaveDTO {

    private Long id;

    @NotBlank
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @NotBlank
    @Size(max = 32)
    private String riskDomain;

    @NotNull
    @Min(1)
    @Max(9999)
    private Integer version;

    @NotNull
    private Integer status;

    @Size(max = 64)
    private String modelName;

    @DecimalMin("0")
    @DecimalMax("2")
    private java.math.BigDecimal temperature;

    @Min(5000)
    @Max(180000)
    private Integer timeoutMs;

    @NotBlank
    private String instructions;

    @NotBlank
    @Size(max = 512)
    private String allowedCategories;

    private Integer sortOrder;
}
