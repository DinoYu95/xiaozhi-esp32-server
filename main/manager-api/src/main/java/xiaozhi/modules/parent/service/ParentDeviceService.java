package xiaozhi.modules.parent.service;

import java.util.List;

import xiaozhi.modules.parent.dto.ParentDeviceBindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceUnbindDTO;
import xiaozhi.modules.parent.vo.ParentDeviceItemVO;

/**
 * 家长端设备绑定服务
 */
public interface ParentDeviceService {

    /**
     * 通过绑定码绑定设备，返回 deviceId
     */
    BindResult bind(Long parentUserId, ParentDeviceBindDTO dto);

    /**
     * 解绑设备（仅删除绑定关系，不删 ai_device）
     */
    void unbind(Long parentUserId, ParentDeviceUnbindDTO dto);

    /**
     * 当前家长已绑定设备列表
     */
    List<ParentDeviceItemVO> list(Long parentUserId);

    record BindResult(String deviceId, String message) {
    }
}
