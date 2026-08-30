package xiaozhi.modules.ota.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "Beta 回滚（可选指定目标包）")
public class ReleaseRollbackDTO {

    @Schema(description = "回滚到指定 package_id；为空则重激活 previous_release")
    private String packageId;
}
