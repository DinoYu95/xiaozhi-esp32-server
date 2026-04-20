package xiaozhi.modules.parent.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "家长影子任务分页")
public class ParentShadowMissionPageVO {

    @Schema(description = "当前页数据")
    private List<ParentShadowMissionDetailVO> list;

    @Schema(description = "总条数")
    private Long total;

    @Schema(description = "当前页码，从 1 开始")
    private Integer page;

    @Schema(description = "每页条数")
    private Integer pageSize;

    @Schema(description = "是否还有下一页")
    private Boolean hasMore;
}
