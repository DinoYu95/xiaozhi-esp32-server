package xiaozhi.modules.risk.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChildRiskSignalResultVO {
    private Long eventId;
    private boolean suppressed;
    private String suppressedReason;
}
