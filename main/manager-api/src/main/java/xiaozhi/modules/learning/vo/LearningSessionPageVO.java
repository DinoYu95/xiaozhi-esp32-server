package xiaozhi.modules.learning.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "作业辅导 session 分页")
public class LearningSessionPageVO {

    private List<LearningSessionItemVO> list;
    private long total;
    private int page;
    private int pageSize;
    private boolean hasMore;
}
