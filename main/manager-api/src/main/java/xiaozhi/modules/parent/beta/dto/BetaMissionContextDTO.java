package xiaozhi.modules.parent.beta.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "锁定体验对象")
public class BetaMissionContextDTO {

    @NotNull(message = "childId 不能为空")
    private Long childId;
}
