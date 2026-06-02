package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_risk_preference")
public class ParentRiskPreferenceEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private Long childId;
    /** JSON 数组 */
    private String focusDomains;
    private Date createTime;
    private Date updateTime;
}
