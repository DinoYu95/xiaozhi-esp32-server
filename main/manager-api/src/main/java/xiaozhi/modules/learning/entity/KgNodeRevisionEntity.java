package xiaozhi.modules.learning.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("kg_node_revision")
public class KgNodeRevisionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long graphReleaseId;
    private Long nodeId;
    private String name;
    private String description;
    private Integer grade;
    private String properties;
}
