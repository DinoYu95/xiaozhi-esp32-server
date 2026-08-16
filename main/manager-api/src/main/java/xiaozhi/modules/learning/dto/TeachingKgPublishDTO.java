package xiaozhi.modules.learning.dto;

import java.util.List;

import lombok.Data;

@Data
public class TeachingKgPublishDTO {

    private String versionLabel;
    /** 教研 draft meta.id，如 p42-g3 */
    private String draftMetaId;
    private String subject;
    private String provinceCode;
    private String cityCode;
    private String semester;
    private String textbookEdition;
    private int grade;
    private Long teachingSubmissionId;
    private List<TeachingKgModuleDTO> modules;
    private List<TeachingKgNodeDTO> nodes;
    private List<TeachingKgEdgeDTO> edges;

    @Data
    public static class TeachingKgModuleDTO {
        private String code;
        private String name;
        private String description;
        private Integer sortOrder;
    }

    @Data
    public static class TeachingKgNodeDTO {
        private String code;
        private String nodeType;
        private String name;
        private String description;
        private Integer grade;
        private String moduleCode;
        private String moduleName;
        private Integer moduleSortOrder;
        private String teachingContent;
        private List<MasteryLevelDTO> masteryRubric;
    }

    @Data
    public static class MasteryLevelDTO {
        private String level;
        private String criteria;
    }

    @Data
    public static class TeachingKgEdgeDTO {
        private String fromCode;
        private String toCode;
        private String edgeType;
    }
}
