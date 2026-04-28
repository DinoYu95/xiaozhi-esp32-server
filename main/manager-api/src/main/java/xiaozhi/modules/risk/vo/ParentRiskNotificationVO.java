package xiaozhi.modules.risk.vo;

import java.util.Date;

import lombok.Data;

@Data
public class ParentRiskNotificationVO {
    private Long id;
    private Long childId;
    private Long eventId;
    private String title;
    private String summary;
    private Integer riskLevel;
    private Integer isRead;
    private Date createTime;
}
