package xiaozhi.modules.parent.service;

import xiaozhi.modules.parent.vo.ParentSnapshotPrepareVO;
import xiaozhi.modules.parent.vo.ParentSnapshotStatusVO;
import xiaozhi.modules.parent.vo.ParentChatSnapshotUploadResultVO;

public interface ParentSnapshotService {

    ParentSnapshotPrepareVO prepare(String deviceId, String requestId, String uploadBaseUrl);

    void deviceUpload(String requestId, String uploadToken, byte[] bytes, String mimeType, Integer width,
            Integer height, String taskType);

    ParentSnapshotStatusVO getStatus(String requestId);

    ParentChatSnapshotUploadResultVO finalizeSnapshot(String requestId, Long parentUserId, Long childId,
            Long assistantMessageId);

    /** 设备已上传且聊天记录已写入时，尝试把 OSS objectKey 绑定到助手消息。 */
    void tryBindAssistantMessage(Long assistantMessageId, String requestId);
}
