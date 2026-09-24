package xiaozhi.modules.parent.betaconfidentiality.vo;

import java.util.Date;

import lombok.Data;

@Data
public class ParentBetaConfPendingUserVO {

    private Long parentUserId;
    private String nickname;
    private Date createTime;
}
