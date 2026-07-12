package xiaozhi.modules.parent.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "批量更新设备风险通知订阅")
public class DeviceRiskNotifySubscriberUpdateDTO {

    @NotNull
    @Valid
    @Schema(description = "要更新的 Member 列表（部分更新）")
    private List<DeviceRiskNotifySubscriberItemDTO> items;
}
