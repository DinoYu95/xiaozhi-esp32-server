package xiaozhi.modules.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("kg_closure")
public class KgClosureEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long graphReleaseId;
    private String relationType;
    private Long ancestorNodeId;
    private Long descendantNodeId;
    private Integer minDepth;
}
