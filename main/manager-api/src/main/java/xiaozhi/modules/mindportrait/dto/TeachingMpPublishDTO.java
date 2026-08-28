package xiaozhi.modules.mindportrait.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "教研审批通过后发布心绪图谱模板")
public class TeachingMpPublishDTO {

    private String ageBand;
    private String versionLabel;
    private Long teachingSubmissionId;
    private String rulesJson;
    private List<Node> nodes;
    private List<Edge> edges;

    @Data
    public static class Node {
        private String code;
        private String nodeType;
        private String parentCode;
        private String label;
        private String shortLabel;
        private String shortDesc;
        private String clusterCode;
        private Integer sortOrder;
        private Integer requiredEvidence;
        private Integer visibleThreshold;
        private Integer strongThreshold;
        private List<String> matchHints;
        private String propertiesJson;
    }

    @Data
    public static class Edge {
        private String fromCode;
        private String toCode;
        private String edgeType;
    }
}
