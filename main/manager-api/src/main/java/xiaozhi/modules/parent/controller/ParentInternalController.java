package xiaozhi.modules.parent.controller;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.agent.service.AgentChatHistoryService;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentChatHistoryDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserTokenDao;
import xiaozhi.modules.parent.service.ParentDeviceRuleService;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.vo.ParentZhibanMemoryContextVO;
import xiaozhi.modules.parent.entity.ParentChatHistoryEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserTokenEntity;

/**
 * 家长端内部接口：供 xiaozhi-server 调用，需 Bearer server.secret 鉴权。
 */
@RestController
@RequestMapping("/config/parent")
@RequiredArgsConstructor
@Slf4j
public class ParentInternalController {

    private static final byte CHAT_TYPE_PARENT = 1;
    private static final byte CHAT_TYPE_ASSISTANT = 2;

    private final ParentUserTokenDao parentUserTokenDao;
    private final DeviceChildDao deviceChildDao;
    private final DeviceDao deviceDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final ParentChatHistoryDao parentChatHistoryDao;
    private final AgentChatHistoryService agentChatHistoryService;
    private final ParentDeviceRuleService parentDeviceRuleService;

    /**
     * 获取孩子与助手的近期对话记录（格式化为「孩子：xxx\n助手：yyy」）。
     * 供 zhiban-agent 在家长询问「你们最近聊了什么」时主动拉取，按需查询而非每次传全文。
     *
     * @param agentId   智能体ID
     * @param macAddress 设备MAC地址（与 ai_agent_chat_history 一致）
     * @param limit      最多条数，默认 30
     */
    /**
     * 家长小程序向 zhiban 询问孩子情况时：提供与设备端一致的 user_id（deviceId_childId）、
     * agent/mac 以及孩子静态档案摘要，避免记忆命名空间不一致导致检索为空、模型胡编。
     */
    @GetMapping("/zhiban-memory-context")
    public Result<ParentZhibanMemoryContextVO> getZhibanMemoryContext(
            @RequestParam Long parentUserId,
            @RequestParam Long childId) {
        if (parentUserId == null || childId == null) {
            return new Result<ParentZhibanMemoryContextVO>().error(ErrorCode.PARAMS_GET_ERROR, "parentUserId、childId 必填");
        }
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            return new Result<ParentZhibanMemoryContextVO>().error(ErrorCode.PARAMS_GET_ERROR, "孩子不存在");
        }
        String deviceId = child.getDeviceId();
        if (StringUtils.isBlank(deviceId)) {
            return new Result<ParentZhibanMemoryContextVO>().error(ErrorCode.AGENT_NOT_FOUND, "设备未绑定");
        }
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            return new Result<ParentZhibanMemoryContextVO>().error(ErrorCode.AGENT_NOT_FOUND);
        }
        String normalized = deviceId.replace(":", "_").toLowerCase();
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
        if (binding == null) {
            return new Result<ParentZhibanMemoryContextVO>().error(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        String zhibanUserId = deviceId + "_" + child.getId();
        String mac = StringUtils.isNotBlank(device.getMacAddress()) ? device.getMacAddress().trim() : deviceId;
        String profile = buildDeviceChildProfileSummary(child);
        ParentZhibanMemoryContextVO vo = new ParentZhibanMemoryContextVO(
                zhibanUserId,
                device.getAgentId(),
                mac,
                StringUtils.defaultString(child.getName(), "").trim(),
                profile);
        return new Result<ParentZhibanMemoryContextVO>().ok(vo);
    }

    private String buildDeviceChildProfileSummary(DeviceChildEntity child) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(child.getName())) {
            sb.append("姓名/昵称：").append(child.getName().trim()).append("；");
        }
        if (child.getBirthday() != null) {
            sb.append("生日：").append(child.getBirthday()).append("；");
        }
        if (StringUtils.isNotBlank(child.getAgeStage())) {
            sb.append("年龄段：").append(child.getAgeStage().trim()).append("；");
        }
        if (StringUtils.isNotBlank(child.getHobbies())) {
            sb.append("爱好：").append(child.getHobbies().trim()).append("；");
        }
        if (StringUtils.isNotBlank(child.getFavoriteTopics())) {
            sb.append("喜欢的话题：").append(child.getFavoriteTopics().trim()).append("；");
        }
        if (StringUtils.isNotBlank(child.getFavoriteStories())) {
            sb.append("喜欢的故事/绘本：").append(child.getFavoriteStories().trim()).append("；");
        }
        if (StringUtils.isNotBlank(child.getPersonalityNote())) {
            sb.append("性格/偏好备注：").append(child.getPersonalityNote().trim()).append("；");
        }
        if (StringUtils.isNotBlank(child.getSchool())) {
            sb.append("学校/幼儿园：").append(child.getSchool().trim()).append("；");
        }
        String s = sb.toString();
        return s.endsWith("；") ? s.substring(0, s.length() - 1) : s;
    }

    @GetMapping("/child-chat-history")
    public Result<String> getChildChatHistory(
            @RequestParam String agentId,
            @RequestParam String macAddress,
            @RequestParam(defaultValue = "30") int limit) {
        if (org.apache.commons.lang3.StringUtils.isBlank(agentId) || org.apache.commons.lang3.StringUtils.isBlank(macAddress)) {
            return new Result<String>().error(ErrorCode.PARAMS_GET_ERROR, "agentId 和 macAddress 必填");
        }
        int safeLimit = Math.min(Math.max(1, limit), 100);
        String childName = null; // 可从 environment_context 传入，此处简化用「孩子」
        String formatted = agentChatHistoryService.getFormattedRecentByAgentAndMac(
                agentId, macAddress.trim(), safeLimit, childName);
        return new Result<String>().ok(formatted != null ? formatted : "");
    }

    /**
     * 校验家长 token，返回 parentUserId。
     * xiaozhi-server 建立 WebSocket 连接时调用。
     */
    @GetMapping("/validate-token")
    public Result<Long> validateToken(@RequestParam String token) {
        if (StringUtils.isBlank(token)) {
            return new Result<Long>().error(ErrorCode.UNAUTHORIZED);
        }
        ParentUserTokenEntity entity = parentUserTokenDao.selectOne(
                new LambdaQueryWrapper<ParentUserTokenEntity>()
                        .eq(ParentUserTokenEntity::getToken, token));
        if (entity == null || entity.getExpireTime() == null
                || entity.getExpireTime().before(new Date())) {
            return new Result<Long>().error(ErrorCode.UNAUTHORIZED);
        }
        return new Result<Long>().ok(entity.getParentUserId());
    }

    /**
     * 保存家长聊天记录。xiaozhi-server WebSocket 对话完成后调用。
     *
     * @param body { parentUserId, childId, content, audioId?, reply }
     */
    @PostMapping("/chat/save")
    public Result<Void> saveChat(@RequestBody ParentChatSaveRequest body) {
        if (body.getParentUserId() == null || body.getChildId() == null
                || StringUtils.isBlank(body.getContent()) || StringUtils.isBlank(body.getReply())) {
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR);
        }
        DeviceChildEntity child = deviceChildDao.selectById(body.getChildId());
        if (child == null) {
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, "孩子不存在");
        }
        String deviceId = child.getDeviceId();
        if (StringUtils.isBlank(deviceId)) {
            return new Result<Void>().error(ErrorCode.AGENT_NOT_FOUND);
        }
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            return new Result<Void>().error(ErrorCode.AGENT_NOT_FOUND);
        }
        String agentId = device.getAgentId();
        String normalized = deviceId.replace(":", "_").toLowerCase();
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, body.getParentUserId())
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
        if (binding == null) {
            return new Result<Void>().error(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        String sessionId = "parent_" + body.getParentUserId() + "_" + child.getId();
        Date now = new Date();

        ParentChatHistoryEntity userMsg = new ParentChatHistoryEntity();
        userMsg.setParentUserId(body.getParentUserId());
        userMsg.setChildId(child.getId());
        userMsg.setDeviceId(deviceId);
        userMsg.setAgentId(agentId);
        userMsg.setSessionId(sessionId);
        userMsg.setChatType(CHAT_TYPE_PARENT);
        userMsg.setContent(body.getContent());
        userMsg.setAudioId(StringUtils.isNotBlank(body.getAudioId()) ? body.getAudioId() : null);
        userMsg.setCreateTime(now);
        parentChatHistoryDao.insert(userMsg);

        ParentChatHistoryEntity assistantMsg = new ParentChatHistoryEntity();
        assistantMsg.setParentUserId(body.getParentUserId());
        assistantMsg.setChildId(child.getId());
        assistantMsg.setDeviceId(deviceId);
        assistantMsg.setAgentId(agentId);
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setChatType(CHAT_TYPE_ASSISTANT);
        assistantMsg.setContent(body.getReply());
        assistantMsg.setAudioId(null);
        assistantMsg.setCreateTime(now);
        parentChatHistoryDao.insert(assistantMsg);
        return new Result<Void>().ok(null);
    }

    /**
     * 添加家长设备规则（供 zhiban-agent 在家长对话中识别到设置规则意图时调用）。
     * 鉴权：Bearer server.secret。
     *
     * @param body parentUserId、macAddress、ruleText
     */
    @PostMapping("/device-rule")
    public Result<ParentDeviceRuleAddResult> addDeviceRule(@RequestBody ParentDeviceRuleAddRequest body) {
        if (body.getParentUserId() == null || StringUtils.isBlank(body.getMacAddress()) || StringUtils.isBlank(body.getRuleText())) {
            return new Result<ParentDeviceRuleAddResult>().error(ErrorCode.PARAMS_GET_ERROR, "parentUserId、macAddress、ruleText 必填");
        }
        try {
            var vo = parentDeviceRuleService.addByMacAndParent(
                    body.getParentUserId(), body.getMacAddress().trim(), body.getRuleText().trim());
            return new Result<ParentDeviceRuleAddResult>().ok(new ParentDeviceRuleAddResult(vo.getId(), vo.getRuleText()));
        } catch (Exception e) {
            log.warn("添加家长规则失败: {}", e.getMessage());
            return new Result<ParentDeviceRuleAddResult>().error(ErrorCode.PARAMS_GET_ERROR, e.getMessage());
        }
    }

    /** 添加规则请求体 */
    @lombok.Data
    public static class ParentDeviceRuleAddRequest {
        private Long parentUserId;
        private String macAddress;
        private String ruleText;
    }

    /** 添加规则响应 */
    @lombok.Data
    public static class ParentDeviceRuleAddResult {
        private Long id;
        private String ruleText;
        public ParentDeviceRuleAddResult(Long id, String ruleText) {
            this.id = id;
            this.ruleText = ruleText;
        }
    }

    /** 保存请求体 */
    @lombok.Data
    public static class ParentChatSaveRequest {
        private Long parentUserId;
        private Long childId;
        private String content;
        private String audioId;
        private String reply;
    }
}
