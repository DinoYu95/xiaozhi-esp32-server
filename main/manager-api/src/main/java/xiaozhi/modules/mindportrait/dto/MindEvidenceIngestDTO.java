package xiaozhi.modules.mindportrait.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "写入成长观测证据（设备/对话回调）")
public class MindEvidenceIngestDTO {

    private Long childId;
    private String sourceType;
    private String sourceRef;
    /** 对话摘要或任务描述，用于匹配预置 signal */
    private String text;
    /** 可选：直接指定 signal node code */
    private String nodeCode;
    private Integer confidence;
}
