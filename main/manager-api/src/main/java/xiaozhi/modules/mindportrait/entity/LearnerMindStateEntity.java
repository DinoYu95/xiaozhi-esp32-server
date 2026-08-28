package xiaozhi.modules.mindportrait.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("learner_mind_state")
public class LearnerMindStateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private Long releaseId;
    private String nodeCode;
    private Integer evidenceCount;
    private Integer strength;
    private String state;
    private Double visualIntensity;
    private String visualTier;
    private Date firstStrongAt;
    private Date updateTime;
}
