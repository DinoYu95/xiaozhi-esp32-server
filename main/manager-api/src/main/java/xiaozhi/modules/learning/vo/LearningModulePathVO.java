package xiaozhi.modules.learning.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "模块内学习顺序（拓扑序）")
public class LearningModulePathVO {

    private String moduleKey;
    private String moduleLabel;
    private Integer grade;
    private List<LearningMasterySkillVO> path;
}
