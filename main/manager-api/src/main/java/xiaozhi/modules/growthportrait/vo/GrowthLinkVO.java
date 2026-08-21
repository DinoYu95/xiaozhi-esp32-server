package xiaozhi.modules.growthportrait.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class GrowthLinkVO {

    private String source;
    private String target;
    private double strength;
}
