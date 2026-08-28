package xiaozhi.modules.mindportrait.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("learner_mind_evidence")
public class LearnerMindEvidenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private Long releaseId;
    private String nodeCode;
    private String sourceType;
    private String sourceRef;
    private Integer confidence;
    private String snippet;
    private Date createTime;
}
