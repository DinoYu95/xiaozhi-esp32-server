package xiaozhi.modules.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** zhiban-agent 拉取（Bearer server.secret）：本地扫描节奏与总开关副本 */
@Data
@Schema(description = "智伴儿童风险运行时参数")
public class ChildRiskAgentRuntimeVO {
    @Schema(description = "与 server.child_risk_config.enabled 一致；false 时仍可拉规则但上报会 DISABLED")
    private boolean enabled;

    @Schema(description = "每 N 轮对话做一次本地规则扫描，1~99")
    private int evalEveryNRounds;

    private String judgmentMode;
    private boolean routerEnabled;
    private int maxDomainsPerRound;
    private double minConfidenceToAlert;
}
