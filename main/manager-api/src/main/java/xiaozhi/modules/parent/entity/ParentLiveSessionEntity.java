package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_live_session")
public class ParentLiveSessionEntity {

    public static final String STATUS_STARTING = "starting";
    public static final String STATUS_LIVE = "live";
    public static final String STATUS_STOPPING = "stopping";
    public static final String STATUS_STOPPED = "stopped";
    public static final String STATUS_FAILED = "failed";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionNo;
    private Long parentUserId;
    private String deviceId;
    private Long childId;
    private String status;
    private String streamApp;
    private String streamName;
    private String pushUrl;
    private String playUrlFlv;
    private String playUrlHls;
    private Date pushExpireAt;
    private String clientId;
    private String failCode;
    private String failMessage;
    private Date startedAt;
    private Date stoppedAt;
    private String stopReason;
    private Date lastHeartbeatAt;
    private Date createTime;
    private Date updateTime;
}
