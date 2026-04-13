package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建/更新影子任务结果")
public class ParentShadowMissionUpsertResultVO {

    private Long id;
    private String title;
}
