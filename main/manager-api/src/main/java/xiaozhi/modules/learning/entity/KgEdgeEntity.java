package xiaozhi.modules.learning.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("kg_edge")
public class KgEdgeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long graphReleaseId;
    private Long fromNodeId;
    private Long toNodeId;
    private String edgeType;
    private Boolean required;
    private BigDecimal strength;
    private String properties;
}
