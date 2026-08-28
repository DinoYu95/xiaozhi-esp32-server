package xiaozhi.modules.mindportrait.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "会话级成长观测（对话结束后 batch 分析）")
public class MindEvidenceSessionDTO {

    private Long childId;
    private String sourceType;
    private String sourceRef;
    /** 可选：已格式化的 transcript，缺省则由 turns 拼接 */
    private String transcript;
    private List<Turn> turns;

    @Data
    public static class Turn {
        /** user | assistant */
        private String role;
        private String text;
    }
}
