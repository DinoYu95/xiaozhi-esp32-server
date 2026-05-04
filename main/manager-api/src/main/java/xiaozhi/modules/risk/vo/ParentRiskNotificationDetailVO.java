package xiaozhi.modules.risk.vo;

import java.util.Date;

import lombok.Data;

/** 家长小程序：风险提示通知详情（含关联 child_risk_event 可读字段） */
@Data
public class ParentRiskNotificationDetailVO {

    /** parent_risk_notification.id */
    private Long id;
    private Long childId;
    private Long eventId;
    private String title;
    /** 列表摘要，可能与 reasonPublic 同源截断 */
    private String summary;
    /** 1 最严重，3 最轻 */
    private Integer riskLevel;
    /** 0 未读，1 已读 */
    private Integer isRead;
    private Date createTime;

    /** 以下为关联事件（child_risk_event），事件缺失时可能为 null */
    private String category;
    /** 对外可读说明（如命中规则片段） */
    private String reasonPublic;
    private String sessionId;
    /** 如 RULE、ZhibAN_JSON */
    private String source;
    /** WAIT_NOTIFY / DONE / SUPPRESSED 等 */
    private String eventStatus;
    private Date eventCreateTime;
}
