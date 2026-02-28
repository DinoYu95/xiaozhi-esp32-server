package xiaozhi.modules.parent.service;

import xiaozhi.modules.parent.dto.DeviceChildSaveDTO;
import xiaozhi.modules.parent.dto.DeviceChildUpdateDTO;
import xiaozhi.modules.parent.vo.DeviceChildVO;

/**
 * 设备主孩子 Service
 */
public interface DeviceChildService {

    /** 添加或更新设备主孩子（一设备一孩） */
    DeviceChildVO saveOrUpdate(Long parentUserId, DeviceChildSaveDTO dto);

    /** 根据设备ID获取主孩子信息，未绑定则 null */
    DeviceChildVO getByDeviceId(Long parentUserId, String deviceId);

    /** 更新主孩子信息 */
    void update(Long parentUserId, DeviceChildUpdateDTO dto);

    /** 解除设备主孩子（物理删除孩子及其声纹记录） */
    void deleteByDeviceId(Long parentUserId, String deviceId);
}
