package xiaozhi.modules.parent.service;

import java.util.List;

import xiaozhi.modules.parent.dto.ParentDeviceBindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceNameUpdateDTO;
import xiaozhi.modules.parent.dto.ParentDeviceSkillBindDTO;
import xiaozhi.modules.parent.dto.ParentDeviceUnbindDTO;
import xiaozhi.modules.parent.vo.ParentDeviceItemVO;
import xiaozhi.modules.parent.vo.ParentDeviceSkillVO;

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

    /**
     * 修改设备名称（写入 ai_device.alias，设备重连后对话自称生效）
     */
    void updateDeviceName(Long parentUserId, String deviceId, ParentDeviceNameUpdateDTO dto);

    /**
     * 获取某设备下所有技能信息（需校验设备已绑定给当前家长）
     */
    List<ParentDeviceSkillVO> listSkills(Long parentUserId, String deviceId);

    /**
     * 获取某设备已绑定的技能 id 列表（用于去重；官方=String ai_skill.id，家长=Long parent_user_skill.id）
     */
    List<Object> listBoundSkillIds(Long parentUserId, String deviceId);

    /**
     * 绑定技能到设备（支持按 speakerType  targeting）
     */
    void bindSkill(Long parentUserId, String deviceId, ParentDeviceSkillBindDTO dto);

    /**
     * 解绑设备的某个技能（需指定 speakerType）
     */
    void unbindSkill(Long parentUserId, String deviceId, String skillSource, Object skillId, String speakerType);

    record BindResult(String deviceId, String message) {
    }
}
