package xiaozhi.modules.learning.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("kg_node")
public class KgNodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String nodeType;
    private Date createTime;
}
