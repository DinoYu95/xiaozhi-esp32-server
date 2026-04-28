package xiaozhi.modules.risk.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParentRiskNotificationPageVO {
    private List<ParentRiskNotificationVO> list;
    private Long total;
    private Integer page;
    private Integer pageSize;
    private Boolean hasMore;
}
