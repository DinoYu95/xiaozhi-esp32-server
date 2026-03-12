package xiaozhi.modules.parent.vo;

import lombok.Data;

import java.util.Date;

/**
 * 家长端-设备声纹列表项（含主孩子声纹 + 后台声纹）
 */
@Data
public class ParentDeviceVoicePrintVO {

    /** 声纹ID */
    private String id;
    /** 音频文件ID */
    private String audioId;
    /** 声纹来源姓名 */
    private String sourceName;
    /** 描述 */
    private String introduce;
    /** 创建时间 */
    private Date createDate;

    /**
     * 家长是否可管理（编辑/删除）。
     * true=主孩子声纹，可重新录入、删除；
     * false=后台录入声纹，仅可查看。
     */
    private Boolean canManage;
}
