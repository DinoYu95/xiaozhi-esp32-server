package xiaozhi.modules.parent.controller;

import java.util.Date;
import java.util.List;

import java.util.Base64;

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
import xiaozhi.modules.parent.service.ParentShadowMissionService;
import xiaozhi.modules.parent.service.ParentSnapshotService;
import xiaozhi.modules.parent.storage.ParentStorageCategory;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.vo.ParentChatSaveResultVO;
import xiaozhi.modules.parent.vo.ParentChatSnapshotUploadResultVO;
import xiaozhi.modules.parent.vo.ParentSnapshotPrepareVO;
import xiaozhi.modules.parent.vo.ParentSnapshotStatusVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionActiveVO;
import xiaozhi.modules.parent.vo.ParentShadowMissionUpsertResultVO;
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
    private final ParentShadowMissionService parentShadowMissionService;
    private final ParentStorageService parentStorageService;
    private final ParentSnapshotService parentSnapshotService;

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
                device.getId(),
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
    public Result<ParentChatSaveResultVO> saveChat(@RequestBody ParentChatSaveRequest body) {
        if (body.getParentUserId() == null || body.getChildId() == null
                || StringUtils.isBlank(body.getContent()) || StringUtils.isBlank(body.getReply())) {
            return new Result<ParentChatSaveResultVO>().error(ErrorCode.PARAMS_GET_ERROR);
        }
        DeviceChildEntity child = deviceChildDao.selectById(body.getChildId());
        if (child == null) {
            return new Result<ParentChatSaveResultVO>().error(ErrorCode.PARAMS_GET_ERROR, "孩子不存在");
        }
        String deviceId = child.getDeviceId();
        if (StringUtils.isBlank(deviceId)) {
            return new Result<ParentChatSaveResultVO>().error(ErrorCode.AGENT_NOT_FOUND);
        }
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            return new Result<ParentChatSaveResultVO>().error(ErrorCode.AGENT_NOT_FOUND);
        }
        String agentId = device.getAgentId();
        String normalized = deviceId.replace(":", "_").toLowerCase();
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, body.getParentUserId())
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
        if (binding == null) {
            return new Result<ParentChatSaveResultVO>().error(ErrorCode.PARENT_DEVICE_NOT_BOUND);
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
        userMsg.setMessageKind("text");
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
        if (StringUtils.isNotBlank(body.getSnapshotRequestId())) {
            assistantMsg.setSnapshotRequestId(body.getSnapshotRequestId().trim());
            assistantMsg.setMessageKind("text_with_snapshot");
        } else {
            assistantMsg.setMessageKind("text");
        }
        assistantMsg.setCreateTime(now);
        parentChatHistoryDao.insert(assistantMsg);
        if (StringUtils.isNotBlank(body.getSnapshotRequestId())) {
            parentSnapshotService.tryBindAssistantMessage(assistantMsg.getId(), body.getSnapshotRequestId().trim());
        }
        return new Result<ParentChatSaveResultVO>().ok(
                new ParentChatSaveResultVO(userMsg.getId(), assistantMsg.getId()));
    }

    /**
     * 远程看娃 Phase B：prepare（notify + HTTP 回传，taskType=parent_snapshot）。
     */
    @PostMapping("/chat/snapshot/prepare")
    public Result<ParentSnapshotPrepareVO> prepareChatSnapshot(@RequestBody ParentSnapshotPrepareRequest body) {
        if (body == null || StringUtils.isBlank(body.getDeviceId()) || StringUtils.isBlank(body.getRequestId())) {
            return new Result<ParentSnapshotPrepareVO>().error(ErrorCode.PARAMS_GET_ERROR);
        }
        try {
            ParentSnapshotPrepareVO vo = parentSnapshotService.prepare(
                    body.getDeviceId().trim(),
                    body.getRequestId().trim(),
                    StringUtils.trimToNull(body.getUploadBaseUrl()));
            return new Result<ParentSnapshotPrepareVO>().ok(vo);
        } catch (Exception e) {
            log.warn("远程看娃 prepare 失败: {}", e.getMessage());
            return new Result<ParentSnapshotPrepareVO>().error(ErrorCode.PARAMS_GET_ERROR, e.getMessage());
        }
    }

    /**
     * 远程看娃：查询设备是否已 HTTP 上传画面。
     */
    @GetMapping("/chat/snapshot/status")
    public Result<ParentSnapshotStatusVO> getChatSnapshotStatus(@RequestParam String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return new Result<ParentSnapshotStatusVO>().error(ErrorCode.PARAMS_GET_ERROR);
        }
        return new Result<ParentSnapshotStatusVO>().ok(parentSnapshotService.getStatus(requestId.trim()));
    }

    /**
     * 远程看娃 Phase B：设备已上传后，绑定到助手消息。
     */
    @PostMapping("/chat/snapshot/finalize")
    public Result<ParentChatSnapshotUploadResultVO> finalizeChatSnapshot(
            @RequestBody ParentSnapshotFinalizeRequest body) {
        if (body == null || StringUtils.isBlank(body.getRequestId()) || body.getParentUserId() == null
                || body.getChildId() == null || body.getAssistantMessageId() == null) {
            return new Result<ParentChatSnapshotUploadResultVO>().error(ErrorCode.PARAMS_GET_ERROR);
        }
        try {
            ParentChatSnapshotUploadResultVO vo = parentSnapshotService.finalizeSnapshot(
                    body.getRequestId().trim(),
                    body.getParentUserId(),
                    body.getChildId(),
                    body.getAssistantMessageId());
            return new Result<ParentChatSnapshotUploadResultVO>().ok(vo);
        } catch (Exception e) {
            log.warn("远程看娃 finalize 失败 requestId={}: {}", body.getRequestId(), e.getMessage());
            return new Result<ParentChatSnapshotUploadResultVO>().error(ErrorCode.PARAMS_GET_ERROR, e.getMessage());
        }
    }

    /**
     * 远程看娃：将设备拍照 base64 上传 OSS 并绑定到助手消息。
     */
    @PostMapping("/chat/snapshot/upload")
    public Result<ParentChatSnapshotUploadResultVO> uploadChatSnapshot(
            @RequestBody ParentChatSnapshotUploadRequest body) {
        if (body == null || body.getParentUserId() == null || body.getChildId() == null
                || body.getAssistantMessageId() == null || StringUtils.isBlank(body.getImageBase64())) {
            return new Result<ParentChatSnapshotUploadResultVO>().error(ErrorCode.PARAMS_GET_ERROR);
        }
        ParentChatHistoryEntity msg = parentChatHistoryDao.selectById(body.getAssistantMessageId());
        if (msg == null || !body.getParentUserId().equals(msg.getParentUserId())
                || !body.getChildId().equals(msg.getChildId())
                || msg.getChatType() == null || msg.getChatType() != CHAT_TYPE_ASSISTANT) {
            return new Result<ParentChatSnapshotUploadResultVO>().error(ErrorCode.PARAMS_GET_ERROR, "消息不存在");
        }
        if (StringUtils.isNotBlank(body.getSnapshotRequestId())
                && StringUtils.isNotBlank(msg.getSnapshotRequestId())
                && !body.getSnapshotRequestId().trim().equals(msg.getSnapshotRequestId())) {
            return new Result<ParentChatSnapshotUploadResultVO>().error(ErrorCode.PARAMS_GET_ERROR, "requestId 不匹配");
        }
        String b64 = body.getImageBase64().trim();
        if (b64.contains(",")) {
            b64 = b64.substring(b64.indexOf(',') + 1);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            return new Result<ParentChatSnapshotUploadResultVO>().error(ErrorCode.PARAMS_GET_ERROR, "图片数据无效");
        }
        String mime = StringUtils.defaultIfBlank(body.getMimeType(), "image/jpeg");
        String ext = "jpg";
        if (mime.contains("png")) {
            ext = "png";
        } else if (mime.contains("webp")) {
            ext = "webp";
        } else if (mime.contains("gif")) {
            ext = "gif";
        }
        var upload = parentStorageService.uploadBase64(
                ParentStorageCategory.CHAT_SNAPSHOT, body.getParentUserId(), bytes, mime, ext);
        msg.setImageObjectKey(upload.getObjectKey());
        if (StringUtils.isBlank(msg.getMessageKind()) || "text".equals(msg.getMessageKind())) {
            msg.setMessageKind("text_with_snapshot");
        }
        parentChatHistoryDao.updateById(msg);
        return new Result<ParentChatSnapshotUploadResultVO>().ok(
                new ParentChatSnapshotUploadResultVO(msg.getId(), upload.getObjectKey(), upload.getAccessUrl()));
    }

    /**
     * 添加家长设备规则（供 zhiban-agent 在家长对话中识别到设置规则意图时调用）。
     * 鉴权：Bearer server.secret。
     *
     * @param body parentUserId、macAddress、ruleText
     */
    /**
     * 当前生效的影子任务（供 xiaozhi-server 注入孩子对话）。Bearer server.secret。
     */
    @GetMapping("/shadow-mission/active")
    public Result<List<ParentShadowMissionActiveVO>> getActiveShadowMission(
            @RequestParam String deviceId,
            @RequestParam Long childId) {
        if (StringUtils.isBlank(deviceId) || childId == null) {
            return new Result<List<ParentShadowMissionActiveVO>>().error(ErrorCode.PARAMS_GET_ERROR, "deviceId、childId 必填");
        }
        List<ParentShadowMissionActiveVO> list = parentShadowMissionService.listActive(deviceId.trim(), childId);
        return new Result<List<ParentShadowMissionActiveVO>>().ok(list);
    }

    /**
     * 孩子侧对话工具：将影子任务标为已完成。Bearer server.secret。
     */
    @PostMapping("/shadow-mission/complete")
    public Result<Void> completeShadowMissionByChild(@RequestBody ShadowMissionCompleteRequest body) {
        if (body == null || body.getChildId() == null || body.getMissionId() == null) {
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, "childId、missionId 必填");
        }
        try {
            parentShadowMissionService.completeByChild(body.getChildId(), body.getMissionId());
            return new Result<Void>().ok(null);
        } catch (Exception e) {
            log.warn("影子任务完成回写失败: {}", e.getMessage());
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, e.getMessage());
        }
    }

    /**
     * 新增一条影子任务（不取消其他进行中的任务；同孩子最多 5 条 active）。Bearer server.secret。
     */
    @PostMapping("/shadow-mission")
    public Result<ParentShadowMissionUpsertResultVO> upsertShadowMission(@RequestBody ShadowMissionUpsertRequest body) {
        if (body == null || body.getParentUserId() == null || body.getChildId() == null) {
            return new Result<ParentShadowMissionUpsertResultVO>().error(ErrorCode.PARAMS_GET_ERROR, "parentUserId、childId 必填");
        }
        try {
            int dm = body.getDurationMinutes() != null ? body.getDurationMinutes().intValue() : 30;
            var vo = parentShadowMissionService.upsert(
                    body.getParentUserId(),
                    body.getChildId(),
                    body.getTitle(),
                    body.getInstructions(),
                    dm);
            return new Result<ParentShadowMissionUpsertResultVO>().ok(vo);
        } catch (Exception e) {
            log.warn("影子任务创建失败: {}", e.getMessage());
            return new Result<ParentShadowMissionUpsertResultVO>().error(ErrorCode.PARAMS_GET_ERROR, e.getMessage());
        }
    }

    /**
     * 取消当前 active 影子任务（供 zhiban-agent 工具调用）。
     */
    @PostMapping("/shadow-mission/cancel")
    public Result<Void> cancelShadowMission(@RequestBody ShadowMissionCancelRequest body) {
        if (body == null || body.getParentUserId() == null || body.getChildId() == null) {
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, "parentUserId、childId 必填");
        }
        try {
            parentShadowMissionService.cancel(body.getParentUserId(), body.getChildId());
            return new Result<Void>().ok(null);
        } catch (Exception e) {
            log.warn("影子任务取消失败: {}", e.getMessage());
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, e.getMessage());
        }
    }

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

    @lombok.Data
    public static class ShadowMissionUpsertRequest {
        private Long parentUserId;
        private Long childId;
        private String title;
        private String instructions;
        /** 有效时长（分钟），默认 30，范围由服务端裁剪为 5～180 */
        private Integer durationMinutes;
    }

    @lombok.Data
    public static class ShadowMissionCancelRequest {
        private Long parentUserId;
        private Long childId;
    }

    @lombok.Data
    public static class ShadowMissionCompleteRequest {
        private Long childId;
        private Long missionId;
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
        /** 远程看娃请求 id，可选 */
        private String snapshotRequestId;
    }

    @lombok.Data
    public static class ParentChatSnapshotUploadRequest {
        private Long parentUserId;
        private Long childId;
        private Long assistantMessageId;
        private String snapshotRequestId;
        private String imageBase64;
        private String mimeType;
    }

    @lombok.Data
    public static class ParentSnapshotPrepareRequest {
        private String deviceId;
        private String requestId;
        /** manager-api 公网根地址，用于拼 uploadUrl；缺省用 xiaozhi.parent.public-base-url */
        private String uploadBaseUrl;
    }

    @lombok.Data
    public static class ParentSnapshotFinalizeRequest {
        private String requestId;
        private Long parentUserId;
        private Long childId;
        private Long assistantMessageId;
    }
}
