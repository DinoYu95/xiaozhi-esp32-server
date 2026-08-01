package xiaozhi.modules.learning.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("learning_homework_session")
public class LearningHomeworkSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionUuid;
    private String deviceId;
    private Long childId;
    private Long graphReleaseId;
    private Date startedAt;
    private Date endedAt;
    private String endReason;
    private String observationLevel;
    private Integer userTurnCount;
    private Integer photoCount;
    private Integer longestSilenceSec;
    private String summaryJson;
    private Date createTime;
    private Date updateTime;
}
