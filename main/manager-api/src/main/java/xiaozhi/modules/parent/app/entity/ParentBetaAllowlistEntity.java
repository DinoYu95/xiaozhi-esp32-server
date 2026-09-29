package xiaozhi.modules.parent.app.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_beta_allowlist")
public class ParentBetaAllowlistEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String wechatOpenid;
    private String channel;
    private String phone;
    private String source;
    private Integer enabled;
    private Integer reviewerTest;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
