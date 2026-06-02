package xiaozhi.modules.parent.vo;

import java.util.List;

import lombok.Data;

@Data
public class ParentRiskPreferenceVO {
    private Long childId;
    private List<String> focusDomains;
}
