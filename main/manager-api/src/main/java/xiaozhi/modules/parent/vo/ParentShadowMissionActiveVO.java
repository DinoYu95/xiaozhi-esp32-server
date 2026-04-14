package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "当前生效的家长影子任务（供设备侧注入智伴）")
public class ParentShadowMissionActiveVO {

    @Schema(description = "任务ID")
    private Long id;
    @Schema(description = "短标题")
    private String title;
    @Schema(description = "详细说明")
    private String instructions;
    @Schema(description = "失效时间")
    private Date endsAt;
    @Schema(description = "优先级，越小越优先")
    private Integer priority;
}
