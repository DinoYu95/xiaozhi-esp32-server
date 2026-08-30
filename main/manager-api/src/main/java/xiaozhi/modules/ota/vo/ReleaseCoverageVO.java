package xiaozhi.modules.ota.vo;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "发布覆盖度")
public class ReleaseCoverageVO {

    private Integer eligibleCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer downloadingCount;
    private Integer pendingCount;
    private Double percent;
}
