package xiaozhi.modules.parent.beta.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("beta_mission_user_state")
public class BetaMissionUserStateEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private String campaignCode;
    private Long contextChildId;
    private String stepStates;
    private Integer requiredDoneCount;
    private Date packCompletedAt;
    private Integer popupDismissed;
    private Integer riskAlertVisited;
    private Date createTime;
    private Date updateTime;
}
