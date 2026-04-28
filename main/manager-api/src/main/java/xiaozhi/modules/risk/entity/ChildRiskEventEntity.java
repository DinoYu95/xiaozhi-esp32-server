package xiaozhi.modules.risk.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("child_risk_event")
public class ChildRiskEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private Long childId;
    private Long parentUserId;
    private String sessionId;
    private Integer riskLevel;
    private String category;
    private String source;
    private String reasonPublic;
    private String status;
    private String suppressedReason;
    private Date createTime;
}
