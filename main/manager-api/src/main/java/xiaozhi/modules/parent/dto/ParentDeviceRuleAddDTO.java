package xiaozhi.modules.parent.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 家长添加设备规则 DTO
 */
@Data
@Schema(description = "添加家长设备规则")
public class ParentDeviceRuleAddDTO {

    @Schema(description = "设备ID（mac 格式；也可通过 path/query 传 deviceId）")
    private String deviceId;

    @Schema(description = "规则内容，如「不要讲鬼故事」「少提零食」", required = true)
    @NotBlank(message = "规则内容不能为空")
    @Size(max = 200, message = "规则内容不超过200字")
    private String ruleText;
}
