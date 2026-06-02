package xiaozhi.modules.risk.vo;

import java.util.List;

import lombok.Data;

@Data
public class ParentRiskPreferenceAgentVO {
    private Long childId;
    private List<String> focusDomains;
}
