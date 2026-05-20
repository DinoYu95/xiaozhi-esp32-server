package xiaozhi.modules.risk.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "风险领域目录")
public class ChildRiskDomainVO {
    private String code;
    private String name;
    private List<String> suggestedCategories;
}
