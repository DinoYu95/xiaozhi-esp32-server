package xiaozhi.modules.learning.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("learning_evidence_event")
public class LearningEvidenceEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private Long childId;
    private String eventType;
    private Date occurredAt;
    private String payload;
    private String skillCodes;
    private String misconceptionCodes;
    private BigDecimal confidence;
    private String idempotencyKey;
    private Date createTime;
}
