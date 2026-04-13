package xiaozhi.modules.parent.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.agent.dao.AgentSkillMappingDao;
import xiaozhi.modules.agent.dao.AgentVoicePrintDao;
import xiaozhi.modules.agent.entity.AgentSkillMappingEntity;
import xiaozhi.modules.agent.entity.AgentVoicePrintEntity;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentChatAudioDao;
import xiaozhi.modules.parent.dao.ParentChatHistoryDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.dto.ParentChatSendDTO;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentChatAudioEntity;
import xiaozhi.modules.parent.entity.ParentChatHistoryEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.service.ParentChatService;
import xiaozhi.modules.parent.service.ParentDeviceRuleService;
import xiaozhi.modules.sys.service.SysParamsService;
import xiaozhi.modules.parent.vo.ParentChatHistoryPageVO;
import xiaozhi.modules.parent.vo.ParentChatMessageVO;

/**
 * 家长端聊天服务实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParentChatServiceImpl implements ParentChatService {

    private static final int PLAY_TOKEN_EXPIRE_SECONDS = 300;
    private static final byte CHAT_TYPE_PARENT = 1;
    private static final byte CHAT_TYPE_ASSISTANT = 2;

    private final ParentChatHistoryDao parentChatHistoryDao;
    private final ParentChatAudioDao parentChatAudioDao;
    private final DeviceChildDao deviceChildDao;
    private final DeviceDao deviceDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final AgentSkillMappingDao agentSkillMappingDao;
    private final AgentVoicePrintDao agentVoicePrintDao;
    private final ParentUserDao parentUserDao;
    private final ParentDeviceRuleService parentDeviceRuleService;
    private final RedisUtils redisUtils;
    private final RestTemplate restTemplate;
    private final SysParamsService sysParamsService;

    private static final String PARAM_XIAOZHI_SERVER_URL = "xiaozhi.server.url";

    @Override
    public String uploadAudio(Long parentUserId, Long childId, MultipartFile file) {
        ensureParentCanAccessChild(parentUserId, childId);
        if (file == null || file.isEmpty()) {
            throw new RenException("请上传音频");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new RenException("音频不超过 10MB");
        }
        try {
            byte[] bytes = file.getBytes();
            ParentChatAudioEntity entity = new ParentChatAudioEntity();
            entity.setParentUserId(parentUserId);
            entity.setAudio(bytes);
            parentChatAudioDao.insert(entity);
            return entity.getId();
        } catch (IOException e) {
            throw new RenException("读取音频失败");
        }
    }

    @Override
    public ParentChatMessageVO send(Long parentUserId, ParentChatSendDTO dto) {
        if (StringUtils.isBlank(dto.getContent()) && StringUtils.isBlank(dto.getAudioId())) {
            throw new RenException("请输入文字或上传语音（语音需先转文字）");
        }
        String content = StringUtils.isNotBlank(dto.getContent())
                ? dto.getContent().trim()
                : "";
        if (StringUtils.isBlank(content) && StringUtils.isNotBlank(dto.getAudioId())) {
            throw new RenException("暂不支持纯语音发送，请先在客户端转为文字后发送，或直接输入文字");
        }

        DeviceChildEntity child = deviceChildDao.selectById(dto.getChildId());
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        ensureParentCanAccessChild(parentUserId, child.getId());

        DeviceEntity device = deviceDao.selectById(child.getDeviceId());
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            throw new RenException(ErrorCode.AGENT_NOT_FOUND);
        }

        String sessionId = "parent_" + parentUserId + "_" + child.getId();
        String userId = "parent_" + parentUserId;

        // 1. 调用 xiaozhi-server 获取助手回复（传入 device 以便拉取孩子与助手的对话记录）
        String reply = callXiaozhiServerForChat(parentUserId, content, sessionId, userId, device, child);
        if (StringUtils.isBlank(reply)) {
            reply = "抱歉，小助手暂时无法回复，请稍后再试。";
        }

        // 2. 保存家长消息
        ParentChatHistoryEntity userMsg = new ParentChatHistoryEntity();
        userMsg.setParentUserId(parentUserId);
        userMsg.setChildId(child.getId());
        userMsg.setDeviceId(device.getId());
        userMsg.setAgentId(device.getAgentId());
        userMsg.setSessionId(sessionId);
        userMsg.setChatType(CHAT_TYPE_PARENT);
        userMsg.setContent(content);
        userMsg.setAudioId(StringUtils.isNotBlank(dto.getAudioId()) ? dto.getAudioId() : null);
        userMsg.setCreateTime(new Date());
        parentChatHistoryDao.insert(userMsg);

        // 3. 保存助手回复
        ParentChatHistoryEntity assistantMsg = new ParentChatHistoryEntity();
        assistantMsg.setParentUserId(parentUserId);
        assistantMsg.setChildId(child.getId());
        assistantMsg.setDeviceId(device.getId());
        assistantMsg.setAgentId(device.getAgentId());
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setChatType(CHAT_TYPE_ASSISTANT);
        assistantMsg.setContent(reply);
        assistantMsg.setAudioId(null);
        assistantMsg.setCreateTime(new Date());
        parentChatHistoryDao.insert(assistantMsg);

        return toVO(assistantMsg);
    }

    private String callXiaozhiServerForChat(Long parentUserId, String text, String sessionId, String userId,
            DeviceEntity device, DeviceChildEntity child) {
        String deviceId = device.getId();
        String agentId = device.getAgentId();
        String xiaozhiServerUrl = sysParamsService.getValue(PARAM_XIAOZHI_SERVER_URL, true);
        if (StringUtils.isBlank(xiaozhiServerUrl) || "null".equals(xiaozhiServerUrl) || xiaozhiServerUrl.contains("你的")) {
            log.warn("xiaozhi.server.url 未配置，跳过智伴调用");
            return null;
        }
        String url = xiaozhiServerUrl.replaceAll("/+$", "") + "/internal/parent/chat";
        log.info("家长聊天调用 xiaozhi-server: url={}", url);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("text", text);
            body.put("session_id", sessionId);
            body.put("user_id", userId);
            body.put("device_id", deviceId);
            body.put("agent_id", agentId);
            // 家长端对话：speaker_type=parent，传递家长昵称和孩子信息
            String parentNickname = null;
            if (parentUserId != null) {
                ParentUserEntity parentUser = parentUserDao.selectById(parentUserId);
                if (parentUser != null && StringUtils.isNotBlank(parentUser.getNickname())) {
                    parentNickname = parentUser.getNickname().trim();
                }
            }
            Map<String, Object> speakerContext = new HashMap<>();
            speakerContext.put("speaker_type", "parent");
            speakerContext.put("speaker_name", StringUtils.isNotBlank(parentNickname) ? parentNickname : "家长");
            speakerContext.put("introduction", "家长正在通过小程序与智伴对话，询问关于其孩子或设备相关的问题");
            body.put("speaker_context", speakerContext);
            // 孩子信息 + 家长昵称：供智伴回答「我是谁」「我家孩子是谁」
            Map<String, Object> childContext = new HashMap<>();
            childContext.put("parent_user_id", parentUserId);  // 供 zhiban 调用 add_parent_rule 时使用
            childContext.put("parent_nickname", parentNickname);
            // 孩子姓名：优先 device_child.name，为空时回退到 ai_agent_voice_print.source_name
            if (child != null) {
                childContext.put("child_id", child.getId());
                String childName = StringUtils.isNotBlank(child.getName()) ? child.getName().trim() : null;
                if (childName == null) {
                    AgentVoicePrintEntity vp = agentVoicePrintDao.selectOne(
                            new LambdaQueryWrapper<AgentVoicePrintEntity>()
                                    .eq(AgentVoicePrintEntity::getAgentId, agentId)
                                    .eq(AgentVoicePrintEntity::getChildId, child.getId()));
                    if (vp != null && StringUtils.isNotBlank(vp.getSourceName())) {
                        childName = vp.getSourceName().trim();
                    }
                }
                childContext.put("child_name", childName);
                childContext.put("child_birthday", child.getBirthday() != null ? child.getBirthday().toString() : null);
                childContext.put("child_hobbies", child.getHobbies());
                childContext.put("child_favorite_topics", child.getFavoriteTopics());
                childContext.put("child_school", child.getSchool());
            }
            // 供 zhiban-agent 按需拉取：传 agent_id、mac_address，由 zhiban-agent 在家长问「你们最近聊了什么」时主动调用 manager-api /config/parent/child-chat-history
            if (StringUtils.isNotBlank(device.getMacAddress())) {
                childContext.put("mac_address", device.getMacAddress().trim());
                childContext.put("agent_id", agentId);
            }
            // 家长规则：供智伴在家长聊天时也能遵守（如家长问「你跟孩子说话时要遵守哪些规则」）
            List<String> parentRulesList = parentDeviceRuleService.getRuleTextsByDeviceId(device.getId());
            if (parentRulesList == null || parentRulesList.isEmpty()) {
                if (StringUtils.isNotBlank(device.getMacAddress())) {
                    parentRulesList = parentDeviceRuleService.getRuleTextsByDeviceId(device.getMacAddress());
                }
                if ((parentRulesList == null || parentRulesList.isEmpty()) && StringUtils.isNotBlank(device.getMacAddress())) {
                    parentRulesList = parentDeviceRuleService.getRuleTextsByDeviceId(device.getMacAddress().replace(":", "_").toLowerCase());
                }
            }
            if (parentRulesList != null && !parentRulesList.isEmpty()) {
                childContext.put("parent_rules", parentRulesList);
            }
            body.put("environment_context", childContext);
            log.info("家长聊天：传递 parent_nickname={}, child_name={}（任一为空则对应数据未配置）", parentNickname, childContext.get("child_name"));
            if (childContext.get("child_name") == null && child != null) {
                log.warn("家长聊天：设备主孩子(child_id={})无姓名，device_child.name 与 ai_agent_voice_print.source_name 均空，智伴无法回答「孩子是谁」", child.getId());
            }
            // 家长对应的技能列表
            List<AgentSkillMappingEntity> parentMappings = agentSkillMappingDao.selectList(
                    new LambdaQueryWrapper<AgentSkillMappingEntity>()
                            .eq(AgentSkillMappingEntity::getAgentId, agentId)
                            .eq(AgentSkillMappingEntity::getSpeakerType, "parent"));
            if (parentMappings != null && !parentMappings.isEmpty()) {
                body.put("skill_ids", parentMappings.stream().map(AgentSkillMappingEntity::getSkillId).toList());
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String secret = sysParamsService.getValue(Constant.SERVER_SECRET, true);
            if (StringUtils.isNotBlank(secret) && !"null".equals(secret)) {
                headers.setBearerAuth(secret);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            if (resp.getBody() != null && resp.getBody().containsKey("reply")) {
                Object r = resp.getBody().get("reply");
                String replyStr = r != null ? r.toString() : null;
                if (StringUtils.isBlank(replyStr)) {
                    log.warn("xiaozhi-server 返回 reply 为空, 完整响应: {}", resp.getBody());
                }
                return replyStr;
            }
            log.warn("xiaozhi-server 响应无 reply 字段, 完整响应: {}", resp.getBody());
        } catch (HttpStatusCodeException e) {
            log.error("调用 xiaozhi-server 家长聊天失败: HTTP {} body={}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("调用 xiaozhi-server 家长聊天失败: {}", e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<ParentChatMessageVO> getHistory(Long parentUserId, Long childId) {
        ParentChatHistoryPageVO page = getHistoryPage(parentUserId, childId, 1, Integer.MAX_VALUE);
        return page.getList();
    }

    @Override
    public ParentChatHistoryPageVO getHistoryPage(Long parentUserId, Long childId, int page, int pageSize) {
        ensureParentCanAccessChild(parentUserId, childId);
        int p = Math.max(1, page);
        int size = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        Page<ParentChatHistoryEntity> pageReq = new Page<>(p, size);
        Page<ParentChatHistoryEntity> result = parentChatHistoryDao.selectPage(pageReq,
                new LambdaQueryWrapper<ParentChatHistoryEntity>()
                        .eq(ParentChatHistoryEntity::getParentUserId, parentUserId)
                        .eq(ParentChatHistoryEntity::getChildId, childId)
                        .orderByDesc(ParentChatHistoryEntity::getCreateTime));
        List<ParentChatMessageVO> list = result.getRecords().stream().map(this::toVO).toList();
        long total = result.getTotal();
        boolean hasMore = (long) p * size < total;
        return new ParentChatHistoryPageVO(list, hasMore);
    }

    @Override
    public String getPlayToken(Long parentUserId, String audioId) {
        if (StringUtils.isBlank(audioId)) {
            throw new RenException("音频ID不能为空");
        }
        ParentChatAudioEntity entity = parentChatAudioDao.selectOne(
                new LambdaQueryWrapper<ParentChatAudioEntity>()
                        .eq(ParentChatAudioEntity::getId, audioId)
                        .eq(ParentChatAudioEntity::getParentUserId, parentUserId));
        if (entity == null) {
            throw new RenException("音频不存在或无权访问");
        }
        String token = UUID.randomUUID().toString();
        redisUtils.set(RedisKeys.getParentChatAudioKey(token), audioId, PLAY_TOKEN_EXPIRE_SECONDS);
        return token;
    }

    @Override
    public byte[] getAudioByPlayToken(String token) {
        if (StringUtils.isBlank(token)) return null;
        String audioId = (String) redisUtils.get(RedisKeys.getParentChatAudioKey(token));
        if (StringUtils.isBlank(audioId)) return null;
        redisUtils.delete(List.of(RedisKeys.getParentChatAudioKey(token)));
        ParentChatAudioEntity entity = parentChatAudioDao.selectById(audioId);
        return entity != null ? entity.getAudio() : null;
    }

    private void ensureParentCanAccessChild(Long parentUserId, Long childId) {
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        String deviceId = child.getDeviceId();
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        String normalized = deviceId.replace(":", "_").toLowerCase();
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
    }

    private ParentChatMessageVO toVO(ParentChatHistoryEntity e) {
        ParentChatMessageVO vo = new ParentChatMessageVO();
        vo.setId(e.getId());
        vo.setChatType(e.getChatType());
        vo.setContent(e.getContent());
        vo.setAudioId(e.getAudioId());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
