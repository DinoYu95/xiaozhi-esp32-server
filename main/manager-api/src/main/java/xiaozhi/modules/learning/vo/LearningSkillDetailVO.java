package xiaozhi.modules.learning.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "知识点详情")
public class LearningSkillDetailVO {

    private Long childId;
    private String code;
    private String name;
    private String description;
    private Integer grade;
    private String subject;
    private LearningMasterySkillVO mastery;
    private List<LearningSkillBriefVO> prerequisites;
    private List<LearningSkillBriefVO> nextSkills;
    private List<LearningMisconceptionBriefVO> misconceptions;
    private String parentTip;
}
