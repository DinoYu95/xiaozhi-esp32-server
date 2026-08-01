package xiaozhi.modules.parent.service;

import java.util.Map;

import xiaozhi.modules.parent.dto.ParentLiveStartDTO;
import xiaozhi.modules.parent.vo.ParentLiveStartVO;
import xiaozhi.modules.parent.vo.ParentLiveStatusVO;

public interface ParentLiveService {

    ParentLiveStartVO start(Long parentUserId, ParentLiveStartDTO dto);

    ParentLiveStatusVO stop(Long parentUserId, Long sessionId);

    ParentLiveStatusVO heartbeat(Long parentUserId, Long sessionId);

    ParentLiveStatusVO getStatus(Long parentUserId, Long sessionId);

    ParentLiveStatusVO getActiveForDevice(Long parentUserId, String deviceId);

    void handleTencentStreamEvent(Map<String, Object> body);

    ParentLiveStatusVO getInternalStatus(Long sessionId);
}
