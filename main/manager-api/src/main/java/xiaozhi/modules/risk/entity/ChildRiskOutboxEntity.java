package xiaozhi.modules.risk.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("child_risk_outbox")
public class ChildRiskOutboxEntity {

    public static final String ST_PENDING = "PENDING";
    public static final String ST_SUCCESS = "SUCCESS";
    public static final String ST_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private String channel;
    private String status;
    private Integer attempts;
    private Date nextRetryTime;
    private String failMessage;
    private Date createTime;
    private Date updateTime;
}
