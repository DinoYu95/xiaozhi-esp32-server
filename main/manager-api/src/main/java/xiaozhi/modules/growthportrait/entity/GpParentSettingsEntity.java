package xiaozhi.modules.growthportrait.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("gp_parent_settings")
public class GpParentSettingsEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private Long childId;
    private Integer instantNotifyEnabled;
    private Integer weeklyDigestEnabled;
    private Date updateTime;
}
