package xiaozhi.modules.learning.vo;

import java.math.BigDecimal;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "掌握地图中的单个知识点（SKILL）")
public class LearningMasterySkillVO {

    private String code;
    private String name;
    private String description;
    @Schema(description = "unobserved|need_consolidate|practicing|stable")
    private String status;
    @Schema(description = "0～1，无证据时为 null")
    private BigDecimal pMastery;
    private Integer evidenceCount;
    private Date lastEvidenceAt;
}
