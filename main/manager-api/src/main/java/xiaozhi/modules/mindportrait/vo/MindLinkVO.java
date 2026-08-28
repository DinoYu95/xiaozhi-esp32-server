package xiaozhi.modules.mindportrait.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class MindLinkVO {

    private String source;
    private String target;
    private double strength;
}
