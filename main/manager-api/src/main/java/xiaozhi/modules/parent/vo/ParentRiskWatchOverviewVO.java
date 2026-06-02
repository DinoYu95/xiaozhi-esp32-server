package xiaozhi.modules.parent.vo;

import java.util.List;

import lombok.Data;
import xiaozhi.modules.risk.vo.ChildRiskDomainVO;

@Data
public class ParentRiskWatchOverviewVO {
    private ParentRiskPreferenceVO preference;
    private List<ChildRiskDomainVO> domains;
    private List<ParentRiskWatchVO> myWatches;
    private int maxEvaluatorPerChild;
    private int maxKeywordPerChild;
}
