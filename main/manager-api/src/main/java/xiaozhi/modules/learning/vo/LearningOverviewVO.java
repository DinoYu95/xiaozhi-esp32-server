package xiaozhi.modules.learning.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "学习洞察首页聚合")
public class LearningOverviewVO {

    @Schema(description = "是否已设置 currentGrade")
    private Boolean gradeConfigured;
    @Schema(description = "是否已发布数学图谱")
    private Boolean graphReady;
    @Schema(description = "排查：匹配到的 kg_graph_release.id，null 表示未匹配")
    private Long graphReleaseId;
    @Schema(description = "排查：匹配 release 在当前年级可展示的 SKILL 数")
    private Integer graphSkillCountAtGrade;
    @Schema(description = "排查：device_child.province_code 原值（若与 DMS 不一致说明 jar/SQL 映射问题）")
    private String profileProvinceRaw;
    private Integer currentGrade;
    private String provinceCode;
    private String textbookEdition;
    private String textbookSeries;
    private String subjectsEnabled;
    private LearningWeeklyDigestVO weeklyDigest;
}
