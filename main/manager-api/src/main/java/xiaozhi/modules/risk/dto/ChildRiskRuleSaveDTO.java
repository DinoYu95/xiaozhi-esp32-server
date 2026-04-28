package xiaozhi.modules.risk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChildRiskRuleSaveDTO {

    private Long id;

    @NotBlank
    @Size(max = 64)
    private String name;

    @NotBlank
    private String ruleType;

    @NotBlank
    @Size(max = 512)
    private String pattern;

    @NotNull
    @Min(1)
    @Max(3)
    private Integer riskLevel;

    @NotBlank
    @Size(max = 64)
    private String category;

    private Integer sortOrder;
    @NotNull
    private Integer status;
}
