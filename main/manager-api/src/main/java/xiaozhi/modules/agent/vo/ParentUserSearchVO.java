package xiaozhi.modules.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长用户搜索项（智控台绑定用）")
public class ParentUserSearchVO {
    private Long id;
    private String nickname;
}
