package xiaozhi.modules.learning.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("learner_skill_state")
public class LearnerSkillStateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private Long skillNodeId;
    private Long graphReleaseId;
    private String evidenceStage;
    private BigDecimal pMastery;
    private Integer evidenceCount;
    private Date lastEvidenceAt;
    private Date updateTime;
}
