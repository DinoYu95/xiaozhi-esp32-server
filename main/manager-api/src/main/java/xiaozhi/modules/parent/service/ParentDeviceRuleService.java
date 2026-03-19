package xiaozhi.modules.parent.service;

import java.util.List;

import xiaozhi.modules.parent.dto.ParentDeviceRuleAddDTO;
import xiaozhi.modules.parent.vo.ParentDeviceRuleVO;

/**
 * 家长端设备规则服务（家长为该设备智能体设置的「不要讲什么」等规则）
 */
public interface ParentDeviceRuleService {

    /**
     * 获取某设备的规则列表
     *
     * @param parentUserId 家长用户 ID
     * @param deviceId     设备 ID
     * @return 规则列表
     */
    List<ParentDeviceRuleVO> listByDevice(Long parentUserId, String deviceId);

    /**
     * 添加一条规则
     *
     * @param parentUserId 家长用户 ID
     * @param dto          规则内容
     * @return 新增的规则
     */
    ParentDeviceRuleVO add(Long parentUserId, ParentDeviceRuleAddDTO dto);

    /**
     * 删除一条规则
     *
     * @param parentUserId 家长用户 ID
     * @param id           规则 ID
     */
    void delete(Long parentUserId, Long id);

    /**
     * 按设备 ID 获取规则文本列表（供 getAgentModels 下发，按 create_time 正序）
     *
     * @param deviceId 设备 ID
     * @return 规则文本列表，无则空列表
     */
    List<String> getRuleTextsByDeviceId(String deviceId);

    /**
     * 按设备 ID 获取规则文本列表（供 getAgentModels 下发）
     * @deprecated 使用 {@link #getRuleTextsByDeviceId(String)}
     */
    default List<String> getRulesByDeviceId(String deviceId) {
        return getRuleTextsByDeviceId(deviceId);
    }

    /**
     * 通过 mac_address + parentUserId 添加规则（供 zhiban-agent 在家长对话中识别到设置规则意图时调用）
     *
     * @param parentUserId 家长用户 ID（来自 environment_context）
     * @param macAddress   设备 MAC
     * @param ruleText     规则内容
     * @return 新增的规则 VO，失败抛异常
     */
    ParentDeviceRuleVO addByMacAndParent(Long parentUserId, String macAddress, String ruleText);

    /**
     * 通过 mac_address 添加规则（根据设备绑定查找家长，供无 parentUserId 时使用）
     *
     * @param agentId    智能体 ID
     * @param macAddress 设备 MAC
     * @param ruleText   规则内容
     * @return 是否添加成功
     */
    boolean addByMacAddress(String agentId, String macAddress, String ruleText);
}
