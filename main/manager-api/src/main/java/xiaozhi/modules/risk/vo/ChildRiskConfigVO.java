package xiaozhi.modules.risk.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 管理端展示：与 sys_params.server.child_risk_config JSON 字段一致 */
@Data
@Schema(description = "儿童对话风险全局配置")
public class ChildRiskConfigVO {
    @Schema(description = "是否开启风险链路（上报与家长通知前置）")
    private Boolean enabled;
    /** 同级别事件冷却窗口（分钟） */
    private Integer cooldownMinutes;
    /** 上报的级别 ≤ 此值才可能通知家长；1 最严重 */
    private Integer notifyIfRiskLevelLte;
    /** zhiban 每 N 轮评估一次本地规则（与服务端配置语义对齐，实际由 agent 读取或环境变量兜底） */
    private Integer evalEveryNRounds;
}
