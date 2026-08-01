package xiaozhi.modules.learning.vo;

import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "图谱发布版本")
public class KgReleaseVO {

    private Long id;
    private String versionLabel;
    private String status;
    private String subject;
    private Integer gradeMin;
    private Integer gradeMax;
    private Date publishedAt;
}
