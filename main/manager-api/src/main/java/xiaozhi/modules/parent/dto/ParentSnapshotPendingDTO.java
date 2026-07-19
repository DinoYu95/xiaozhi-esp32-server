package xiaozhi.modules.parent.dto;

import java.io.Serializable;

import lombok.Data;

@Data
public class ParentSnapshotPendingDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String requestId;
    private String deviceId;
    private String clientId;
    private String uploadToken;
    private String status;
    private String objectKey;
    private String accessUrl;
    private Integer width;
    private Integer height;
}
