package xiaozhi.modules.parent.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentLiveSessionDao;
import xiaozhi.modules.parent.dto.ParentLiveStartDTO;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentLiveSessionEntity;
import xiaozhi.modules.parent.live.TencentLiveUrlHelper;
import xiaozhi.modules.parent.service.ParentLiveService;
import xiaozhi.modules.parent.util.ParentDeviceAccessHelper;
import xiaozhi.modules.parent.vo.ParentLiveStartVO;
import xiaozhi.modules.parent.vo.ParentLiveStatusVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParentLiveServiceImpl implements ParentLiveService {

    private static final String PARAM_ENABLED = "parent.live.enabled";
    private static final String PARAM_MAX_DURATION = "parent.live.max_duration_sec";
    private static final String PARAM_HEARTBEAT_TIMEOUT = "parent.live.heartbeat_timeout_sec";
    private static final String PARAM_HEARTBEAT_INTERVAL = "parent.live.heartbeat_interval_sec";
    private static final String PARAM_PUSH_TIMEOUT = "parent.live.push_timeout_sec";
    private static final String PARAM_PUSH_DOMAIN = "parent.live.tencent.push_domain";
    private static final String PARAM_PLAY_DOMAIN = "parent.live.tencent.play_domain";
    private static final String PARAM_APP_NAME = "parent.live.tencent.app_name";
    private static final String PARAM_PUSH_AUTH_KEY = "parent.live.tencent.push_auth_key";
    private static final String PARAM_PUSH_EXPIRE_BUFFER = "parent.live.tencent.push_expire_buffer_sec";
    private static final String PARAM_XIAOZHI_URL = "xiaozhi.server.url";
    private static final String DEFAULT_APP_NAME = "parent";

    private final ParentLiveSessionDao parentLiveSessionDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final DeviceDao deviceDao;
    private final DeviceChildDao deviceChildDao;
    private final SysParamsService sysParamsService;
    private final RedisUtils redisUtils;
    private final RestTemplate restTemplate;

    @Override
    public ParentLiveStartVO start(Long parentUserId, ParentLiveStartDTO dto) {
        requireEnabled();
        String deviceId = StringUtils.trimToEmpty(dto.getDeviceId());
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "deviceId 必填");
        }
        ParentDeviceAccessHelper.requireActiveBinding(parentDeviceBindingDao, parentUserId, deviceId);
        throwIfDeviceHasActiveLive(deviceId, parentUserId);

        DeviceEntity device = requireDevice(deviceId);
        if (dto.getChildId() != null) {
            validateChildAccess(parentUserId, dto.getChildId(), device.getId());
        }

        int maxDurationSec = intParam(PARAM_MAX_DURATION, 600);
        int heartbeatIntervalSec = intParam(PARAM_HEARTBEAT_INTERVAL, 20);
        String appName = stringParam(PARAM_APP_NAME, DEFAULT_APP_NAME);
        String pushDomain = requireParam(PARAM_PUSH_DOMAIN, "未配置 parent.live.tencent.push_domain");
        String playDomain = requireParam(PARAM_PLAY_DOMAIN, "未配置 parent.live.tencent.play_domain");
        String pushAuthKey = requireParam(PARAM_PUSH_AUTH_KEY, "未配置 parent.live.tencent.push_auth_key");

        String sessionNo = "live_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String streamName = TencentLiveUrlHelper.buildStreamName(device.getId(), sessionNo);
        int pushBufferSec = intParam(PARAM_PUSH_EXPIRE_BUFFER, 300);
        long expireEpoch = TencentLiveUrlHelper.defaultPushExpireEpochSec(maxDurationSec, pushBufferSec);
        String pushUrl = TencentLiveUrlHelper.buildPushUrl(pushDomain, appName, streamName, pushAuthKey, expireEpoch);
        String playFlv = TencentLiveUrlHelper.buildPlayFlvUrl(playDomain, appName, streamName);
        String playHls = TencentLiveUrlHelper.buildPlayHlsUrl(playDomain, appName, streamName);
        String clientId = buildMqttClientId(device);

        Date now = new Date();
        ParentLiveSessionEntity row = new ParentLiveSessionEntity();
        row.setSessionNo(sessionNo);
        row.setParentUserId(parentUserId);
        row.setDeviceId(device.getId());
        row.setChildId(dto.getChildId());
        row.setStatus(ParentLiveSessionEntity.STATUS_STARTING);
        row.setStreamApp(appName);
        row.setStreamName(streamName);
        row.setPushUrl(pushUrl);
        row.setPlayUrlFlv(playFlv);
        row.setPlayUrlHls(playHls);
        row.setPushExpireAt(new Date(expireEpoch * 1000L));
        row.setClientId(clientId);
        row.setLastHeartbeatAt(now);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        parentLiveSessionDao.insert(row);

        cacheActiveDeviceSession(device.getId(), row.getId());

        Map<String, Object> notifyResult = callXiaozhiLiveStart(row, maxDurationSec);
        boolean ok = Boolean.TRUE.equals(notifyResult.get("ok"));
        String code = String.valueOf(notifyResult.getOrDefault("code", ""));
        if (!ok) {
            failSession(row, StringUtils.defaultIfBlank(code, "NOTIFY_FAILED"),
                    String.valueOf(notifyResult.getOrDefault("message", "无法连接设备")));
            throw liveRenException(code, String.valueOf(notifyResult.getOrDefault("message", "无法连接设备")));
        }

        ParentLiveStartVO vo = new ParentLiveStartVO();
        vo.setSessionId(row.getId());
        vo.setSessionNo(row.getSessionNo());
        vo.setStatus(row.getStatus());
        vo.setDeviceId(row.getDeviceId());
        vo.setPlayUrl(row.getPlayUrlFlv());
        vo.setPlayUrlHls(row.getPlayUrlHls());
        vo.setMode("flv");
        vo.setMaxDurationSec(maxDurationSec);
        vo.setHeartbeatIntervalSec(heartbeatIntervalSec);
        vo.setMessage("正在连接设备，请稍候…");
        return vo;
    }

    @Override
    public ParentLiveStatusVO stop(Long parentUserId, Long sessionId) {
        ParentLiveSessionEntity row = requireOwnedSession(parentUserId, sessionId);
        return doStop(row, "user");
    }

    @Override
    public ParentLiveStatusVO heartbeat(Long parentUserId, Long sessionId) {
        ParentLiveSessionEntity row = requireOwnedSession(parentUserId, sessionId);
        refreshTimeouts(row);
        if (ParentLiveSessionEntity.STATUS_FAILED.equals(row.getStatus())
                || ParentLiveSessionEntity.STATUS_STOPPED.equals(row.getStatus())) {
            return toStatusVo(row);
        }
        if (!ParentLiveSessionEntity.STATUS_LIVE.equals(row.getStatus())
                && !ParentLiveSessionEntity.STATUS_STARTING.equals(row.getStatus())) {
            return toStatusVo(row);
        }
        row.setLastHeartbeatAt(new Date());
        row.setUpdateTime(new Date());
        parentLiveSessionDao.updateById(row);
        return toStatusVo(row);
    }

    @Override
    public ParentLiveStatusVO getStatus(Long parentUserId, Long sessionId) {
        ParentLiveSessionEntity row = requireOwnedSession(parentUserId, sessionId);
        refreshTimeouts(row);
        return toStatusVo(row);
    }

    @Override
    public ParentLiveStatusVO getActiveForDevice(Long parentUserId, String deviceId) {
        ParentLiveSessionEntity row = findActiveSessionForDevice(deviceId);
        if (row == null || !parentUserId.equals(row.getParentUserId())) {
            return null;
        }
        refreshTimeouts(row);
        return toStatusVo(row);
    }

    @Override
    public void handleTencentStreamEvent(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        String streamName = extractStreamName(body);
        if (StringUtils.isBlank(streamName)) {
            log.warn("腾讯云 live 回调缺少 streamName body={}", JsonUtils.toJsonString(body));
            return;
        }
        ParentLiveSessionEntity row = parentLiveSessionDao.selectOne(
                new LambdaQueryWrapper<ParentLiveSessionEntity>()
                        .eq(ParentLiveSessionEntity::getStreamName, streamName)
                        .orderByDesc(ParentLiveSessionEntity::getId)
                        .last("LIMIT 1"));
        if (row == null) {
            log.warn("腾讯云 live 回调未匹配会话 streamName={}", streamName);
            return;
        }
        int eventType = parseInt(body.get("event_type"), -1);
        if (eventType == 1 || "PUSH".equalsIgnoreCase(String.valueOf(body.get("event_type")))) {
            if (ParentLiveSessionEntity.STATUS_STARTING.equals(row.getStatus())) {
                row.setStatus(ParentLiveSessionEntity.STATUS_LIVE);
                row.setStartedAt(new Date());
                row.setUpdateTime(new Date());
                parentLiveSessionDao.updateById(row);
                log.info("live session 推流成功 sessionNo={} streamName={}", row.getSessionNo(), streamName);
            }
            return;
        }
        if (eventType == 0 || "PUSH_DONE".equalsIgnoreCase(String.valueOf(body.get("event_type")))) {
            if (ParentLiveSessionEntity.STATUS_LIVE.equals(row.getStatus())
                    || ParentLiveSessionEntity.STATUS_STARTING.equals(row.getStatus())) {
                doStop(row, "stream_end");
            }
        }
    }

    @Override
    public ParentLiveStatusVO getInternalStatus(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        ParentLiveSessionEntity row = parentLiveSessionDao.selectById(sessionId);
        if (row == null) {
            return null;
        }
        refreshTimeouts(row);
        return toStatusVo(row);
    }

    private ParentLiveStatusVO doStop(ParentLiveSessionEntity row, String reason) {
        if (ParentLiveSessionEntity.STATUS_STOPPED.equals(row.getStatus())
                || ParentLiveSessionEntity.STATUS_FAILED.equals(row.getStatus())) {
            return toStatusVo(row);
        }
        row.setStatus(ParentLiveSessionEntity.STATUS_STOPPING);
        row.setUpdateTime(new Date());
        parentLiveSessionDao.updateById(row);
        callXiaozhiLiveStop(row, reason);
        row.setStatus(ParentLiveSessionEntity.STATUS_STOPPED);
        row.setStoppedAt(new Date());
        row.setStopReason(reason);
        row.setUpdateTime(new Date());
        parentLiveSessionDao.updateById(row);
        clearActiveDeviceSession(row.getDeviceId(), row.getId());
        log.info("live session 已停止 sessionNo={} reason={}", row.getSessionNo(), reason);
        return toStatusVo(row);
    }

    private void refreshTimeouts(ParentLiveSessionEntity row) {
        if (row == null) {
            return;
        }
        if (ParentLiveSessionEntity.STATUS_STOPPED.equals(row.getStatus())
                || ParentLiveSessionEntity.STATUS_FAILED.equals(row.getStatus())) {
            return;
        }
        int pushTimeoutSec = intParam(PARAM_PUSH_TIMEOUT, 30);
        if (ParentLiveSessionEntity.STATUS_STARTING.equals(row.getStatus()) && row.getCreateTime() != null) {
            long elapsedMs = System.currentTimeMillis() - row.getCreateTime().getTime();
            if (elapsedMs > pushTimeoutSec * 1000L) {
                failSession(row, "PUSH_TIMEOUT", "设备连接超时，请稍后再试");
                callXiaozhiLiveStop(row, "push_timeout");
                return;
            }
        }
        int maxDurationSec = intParam(PARAM_MAX_DURATION, 600);
        Date base = row.getStartedAt() != null ? row.getStartedAt() : row.getCreateTime();
        if (base != null && ParentLiveSessionEntity.STATUS_LIVE.equals(row.getStatus())) {
            long liveMs = System.currentTimeMillis() - base.getTime();
            if (liveMs > maxDurationSec * 1000L) {
                doStop(row, "max_duration");
                return;
            }
        }
        int heartbeatTimeoutSec = intParam(PARAM_HEARTBEAT_TIMEOUT, 60);
        if (ParentLiveSessionEntity.STATUS_LIVE.equals(row.getStatus()) && row.getLastHeartbeatAt() != null) {
            long hbMs = System.currentTimeMillis() - row.getLastHeartbeatAt().getTime();
            if (hbMs > heartbeatTimeoutSec * 1000L) {
                doStop(row, "heartbeat_timeout");
            }
        }
    }

    private void failSession(ParentLiveSessionEntity row, String code, String message) {
        row.setStatus(ParentLiveSessionEntity.STATUS_FAILED);
        row.setFailCode(code);
        row.setFailMessage(message);
        row.setStoppedAt(new Date());
        row.setStopReason("failed");
        row.setUpdateTime(new Date());
        parentLiveSessionDao.updateById(row);
        clearActiveDeviceSession(row.getDeviceId(), row.getId());
    }

    private Map<String, Object> callXiaozhiLiveStart(ParentLiveSessionEntity row, int maxDurationSec) {
        String baseUrl = sysParamsService.getValue(PARAM_XIAOZHI_URL, true);
        if (StringUtils.isBlank(baseUrl) || baseUrl.contains("你的")) {
            log.warn("xiaozhi.server.url 未配置，跳过 live notify");
            return Map.of("ok", false, "code", "GATEWAY_UNAVAILABLE", "message", "服务未配置");
        }
        String url = baseUrl.replaceAll("/+$", "") + "/internal/parent/live/start";
        Map<String, Object> body = new HashMap<>();
        body.put("session_id", row.getId());
        body.put("session_no", row.getSessionNo());
        body.put("device_id", row.getDeviceId());
        body.put("parent_user_id", row.getParentUserId());
        body.put("client_id", row.getClientId());
        Map<String, Object> push = new HashMap<>();
        push.put("mode", "rtmp");
        push.put("url", row.getPushUrl());
        push.put("streamKey", row.getStreamName());
        push.put("appName", row.getStreamApp());
        Map<String, Object> video = new HashMap<>();
        video.put("width", 640);
        video.put("height", 480);
        video.put("fps", 10);
        video.put("bitrate_kbps", 512);
        video.put("codec", "h264");
        push.put("video", video);
        push.put("audio", Map.of("enabled", false));
        push.put("max_duration_sec", maxDurationSec);
        body.put("push", push);
        return postXiaozhiInternal(url, body);
    }

    private void callXiaozhiLiveStop(ParentLiveSessionEntity row, String reason) {
        String baseUrl = sysParamsService.getValue(PARAM_XIAOZHI_URL, true);
        if (StringUtils.isBlank(baseUrl) || baseUrl.contains("你的")) {
            return;
        }
        String url = baseUrl.replaceAll("/+$", "") + "/internal/parent/live/stop";
        Map<String, Object> body = new HashMap<>();
        body.put("session_id", row.getId());
        body.put("session_no", row.getSessionNo());
        body.put("device_id", row.getDeviceId());
        body.put("client_id", row.getClientId());
        body.put("reason", reason);
        postXiaozhiInternal(url, body);
    }

    private Map<String, Object> postXiaozhiInternal(String url, Map<String, Object> body) {
        try {
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
            Map<String, Object> raw = resp.getBody();
            if (raw != null) {
                return raw;
            }
            return Map.of("ok", false, "code", "GATEWAY_UNAVAILABLE", "message", "empty response");
        } catch (Exception e) {
            log.warn("调用 xiaozhi live 失败 url={} err={}", url, e.getMessage());
            return Map.of("ok", false, "code", "GATEWAY_UNAVAILABLE", "message", "无法连接设备服务");
        }
    }

    private ParentLiveSessionEntity requireOwnedSession(Long parentUserId, Long sessionId) {
        if (sessionId == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        ParentLiveSessionEntity row = parentLiveSessionDao.selectById(sessionId);
        if (row == null || !parentUserId.equals(row.getParentUserId())) {
            throw new RenException(ErrorCode.PARENT_LIVE_SESSION_NOT_FOUND);
        }
        return row;
    }

    private DeviceEntity requireDevice(String deviceId) {
        DeviceEntity device = deviceDao.selectById(deviceId.trim());
        if (device == null) {
            String normalized = ParentDeviceAccessHelper.normalizeDeviceId(deviceId);
            device = deviceDao.selectById(normalized);
        }
        if (device == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "设备不存在");
        }
        return device;
    }

    private void validateChildAccess(Long parentUserId, Long childId, String deviceId) {
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "孩子不存在");
        }
        if (!ParentDeviceAccessHelper.deviceIdsEquivalent(child.getDeviceId(), deviceId)) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "孩子与设备不匹配");
        }
        ParentDeviceAccessHelper.requireActiveBinding(parentDeviceBindingDao, parentUserId, deviceId);
    }

    private void throwIfDeviceHasActiveLive(String deviceId, Long parentUserId) {
        ParentLiveSessionEntity active = findActiveSessionForDevice(deviceId);
        if (active == null) {
            return;
        }
        if (parentUserId.equals(active.getParentUserId())) {
            throw new RenException(ErrorCode.PARENT_LIVE_ALREADY_ACTIVE, "当前设备已有进行中的远程查看");
        }
        throw new RenException(ErrorCode.PARENT_LIVE_ALREADY_ACTIVE, "其他家长正在查看该设备");
    }

    private ParentLiveSessionEntity findActiveSessionForDevice(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        List<String> statuses = List.of(
                ParentLiveSessionEntity.STATUS_STARTING,
                ParentLiveSessionEntity.STATUS_LIVE,
                ParentLiveSessionEntity.STATUS_STOPPING);
        String normalized = ParentDeviceAccessHelper.normalizeDeviceId(deviceId);
        String colonForm = ParentDeviceAccessHelper.toColonDeviceId(deviceId);
        return parentLiveSessionDao.selectOne(
                new LambdaQueryWrapper<ParentLiveSessionEntity>()
                        .in(ParentLiveSessionEntity::getStatus, statuses)
                        .and(w -> w.eq(ParentLiveSessionEntity::getDeviceId, deviceId.trim())
                                .or().eq(ParentLiveSessionEntity::getDeviceId, normalized)
                                .or().eq(ParentLiveSessionEntity::getDeviceId, colonForm))
                        .orderByDesc(ParentLiveSessionEntity::getId)
                        .last("LIMIT 1"));
    }

    private void requireEnabled() {
        String enabled = sysParamsService.getValue(PARAM_ENABLED, true);
        if ("false".equalsIgnoreCase(StringUtils.trimToEmpty(enabled))) {
            throw new RenException(ErrorCode.PARENT_LIVE_DISABLED);
        }
    }

    private RenException liveRenException(String code, String message) {
        if ("DEVICE_OFFLINE".equals(code)) {
            return new RenException(ErrorCode.PARAMS_GET_ERROR, message);
        }
        if ("DEVICE_BUSY".equals(code)) {
            return new RenException(ErrorCode.PARENT_LIVE_DEVICE_BUSY, message);
        }
        if ("LIVE_ALREADY_ACTIVE".equals(code)) {
            return new RenException(ErrorCode.PARENT_LIVE_ALREADY_ACTIVE, message);
        }
        return new RenException(ErrorCode.PARAMS_GET_ERROR, message);
    }

    private ParentLiveStatusVO toStatusVo(ParentLiveSessionEntity row) {
        ParentLiveStatusVO vo = new ParentLiveStatusVO();
        vo.setSessionId(row.getId());
        vo.setSessionNo(row.getSessionNo());
        vo.setStatus(row.getStatus());
        vo.setDeviceId(row.getDeviceId());
        vo.setPlayUrl(row.getPlayUrlFlv());
        vo.setPlayUrlHls(row.getPlayUrlHls());
        vo.setMode("flv");
        vo.setFailCode(row.getFailCode());
        vo.setFailMessage(row.getFailMessage());
        int maxDurationSec = intParam(PARAM_MAX_DURATION, 600);
        vo.setMaxDurationSec(maxDurationSec);
        vo.setHeartbeatIntervalSec(intParam(PARAM_HEARTBEAT_INTERVAL, 20));
        Date base = row.getStartedAt() != null ? row.getStartedAt() : row.getCreateTime();
        if (base != null) {
            int elapsed = (int) ((System.currentTimeMillis() - base.getTime()) / 1000L);
            vo.setElapsedSec(Math.max(0, elapsed));
            if (ParentLiveSessionEntity.STATUS_LIVE.equals(row.getStatus())) {
                vo.setRemainingSec(Math.max(0, maxDurationSec - elapsed));
            }
        }
        return vo;
    }

    private void cacheActiveDeviceSession(String deviceId, Long sessionId) {
        redisUtils.set(RedisKeys.getParentLiveDeviceKey(deviceId), sessionId, 7200);
    }

    private void clearActiveDeviceSession(String deviceId, Long sessionId) {
        Object cached = redisUtils.get(RedisKeys.getParentLiveDeviceKey(deviceId));
        if (cached != null && String.valueOf(cached).equals(String.valueOf(sessionId))) {
            redisUtils.delete(RedisKeys.getParentLiveDeviceKey(deviceId));
        }
    }

    private String buildMqttClientId(DeviceEntity device) {
        String macAddress = Optional.ofNullable(device.getMacAddress()).orElse("unknown").replace(":", "_");
        String groupId = Optional.ofNullable(device.getBoard()).orElse("GID_default").replace(":", "_");
        return StrUtil.format("{}@@@{}@@@{}", groupId, macAddress, macAddress);
    }

    private static String extractStreamName(Map<String, Object> body) {
        Object streamId = body.get("stream_id");
        if (streamId != null && StringUtils.isNotBlank(String.valueOf(streamId))) {
            return String.valueOf(streamId).trim();
        }
        Object channelId = body.get("channel_id");
        if (channelId != null) {
            String ch = String.valueOf(channelId).trim();
            int idx = ch.lastIndexOf('_');
            if (idx >= 0 && idx < ch.length() - 1) {
                return ch.substring(idx + 1);
            }
        }
        Object streamName = body.get("stream_name");
        if (streamName != null) {
            return String.valueOf(streamName).trim();
        }
        return null;
    }

    private int intParam(String key, int defaultValue) {
        String v = sysParamsService.getValue(key, true);
        if (StringUtils.isBlank(v)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String stringParam(String key, String defaultValue) {
        String v = sysParamsService.getValue(key, true);
        return StringUtils.isNotBlank(v) ? v.trim() : defaultValue;
    }

    private String requireParam(String key, String message) {
        String v = sysParamsService.getValue(key, true);
        if (StringUtils.isBlank(v)) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, message);
        }
        return v.trim();
    }

    private static int parseInt(Object raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
