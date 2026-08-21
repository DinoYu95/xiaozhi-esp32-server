package xiaozhi.modules.growthportrait.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("gp_template_node")
public class GpTemplateNodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long releaseId;
    private String code;
    private String nodeType;
    private String parentCode;
    private String label;
    private String shortLabel;
    private String shortDesc;
    private String clusterCode;
    private Integer sortOrder;
    private Integer requiredEvidence;
    private Integer visibleThreshold;
    private Integer strongThreshold;
    private String matchHints;
    private String propertiesJson;
}
