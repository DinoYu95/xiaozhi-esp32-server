package xiaozhi.modules.growthportrait.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("gp_template_edge")
public class GpTemplateEdgeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long releaseId;
    private String fromCode;
    private String toCode;
    private String edgeType;
}
