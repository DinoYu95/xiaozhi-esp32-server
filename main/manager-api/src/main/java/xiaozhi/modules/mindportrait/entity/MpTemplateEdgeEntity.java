package xiaozhi.modules.mindportrait.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("mp_template_edge")
public class MpTemplateEdgeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long releaseId;
    private String fromCode;
    private String toCode;
    private String edgeType;
}
