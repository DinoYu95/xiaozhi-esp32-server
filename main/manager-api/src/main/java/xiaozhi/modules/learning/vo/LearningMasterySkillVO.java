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
    @Schema(description = "weekStart 起 7 天内是否有新的学习观察（证据事件）")
    private Boolean observedThisWeek;
    @Schema(description = "与周报 topWeakSkills 同源，本次建议巩固清单（非全部 need_consolidate）")
    private Boolean consolidateThisPeriod;
}
