package xiaozhi.modules.risk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChildRiskConfigSaveDTO {
    @NotNull
    private Boolean enabled;

    @NotNull
    @Min(1)
    @Max(10080)
    private Integer cooldownMinutes;

    /** 1~3，与风险级别一致：1 最严重 */
    @NotNull
    @Min(1)
    @Max(3)
    private Integer notifyIfRiskLevelLte;

    @NotNull
    @Min(1)
    @Max(99)
    private Integer evalEveryNRounds;

    private String judgmentMode;

    private Boolean routerEnabled;

    @Min(1)
    @Max(3)
    private Integer maxDomainsPerRound;

    @Min(0)
    @Max(1)
    private Double minConfidenceToAlert;
}
