package xiaozhi.modules.parent.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 家长设备规则 VO
 */
@Data
@Schema(description = "家长设备规则")
public class ParentDeviceRuleVO {

    @Schema(description = "规则ID")
    private Long id;

    @Schema(description = "规则内容，如「不要讲鬼故事」")
    private String ruleText;

    @Schema(description = "创建时间")
    private Date createTime;
}
