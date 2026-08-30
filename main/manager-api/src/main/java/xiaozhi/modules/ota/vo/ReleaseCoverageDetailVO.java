package xiaozhi.modules.ota.vo;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "发布覆盖度详情")
public class ReleaseCoverageDetailVO extends ReleaseCoverageVO {

    private List<CoverageDeviceVO> devices = new ArrayList<>();
}
