package xiaozhi.modules.risk.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_risk_notification")
public class ParentRiskNotificationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private Long childId;
    private Long eventId;
    private String title;
    private String summary;
    private Integer riskLevel;
    private Integer isRead;
    private Date createTime;
}
