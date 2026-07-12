package xiaozhi.modules.parent.service;

import xiaozhi.modules.parent.dto.DeviceRiskNotifySubscriberUpdateDTO;
import xiaozhi.modules.parent.vo.DeviceRiskNotifyAccessVO;
import xiaozhi.modules.parent.vo.DeviceRiskNotifySubscribersVO;

public interface DeviceRiskNotifyService {

    DeviceRiskNotifySubscribersVO getSubscribers(Long parentUserId, String deviceId);

    void updateSubscribers(Long parentUserId, String deviceId, DeviceRiskNotifySubscriberUpdateDTO dto);

    DeviceRiskNotifyAccessVO getAccess(Long parentUserId, String deviceId);
}
