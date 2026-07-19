package xiaozhi.modules.parent.service.impl;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.ParentChatHistoryDao;
import xiaozhi.modules.parent.dto.ParentSnapshotPendingDTO;
import xiaozhi.modules.parent.entity.ParentChatHistoryEntity;
import xiaozhi.modules.parent.service.ParentSnapshotService;
import xiaozhi.modules.parent.storage.ParentStorageCategory;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.storage.vo.ParentStorageUploadVO;
import xiaozhi.modules.parent.vo.ParentChatSnapshotUploadResultVO;
import xiaozhi.modules.parent.vo.ParentSnapshotPrepareVO;
import xiaozhi.modules.parent.vo.ParentSnapshotStatusVO;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParentSnapshotServiceImpl implements ParentSnapshotService {

    private static final long PENDING_TTL_SECONDS = 300L;
    private static final byte CHAT_TYPE_ASSISTANT = 2;
    private static final long PENDING_PARENT_USER_ID = 0L;
    public static final String TASK_TYPE_PARENT_SNAPSHOT = "parent_snapshot";

    private final DeviceDao deviceDao;
    private final ParentChatHistoryDao parentChatHistoryDao;
    private final ParentStorageService parentStorageService;
    private final RedisUtils redisUtils;

    @Value("${xiaozhi.parent.public-base-url:}")
    private String parentPublicBaseUrl;

    @Override
    public ParentSnapshotPrepareVO prepare(String deviceId, String requestId, String uploadBaseUrl) {
        if (StringUtils.isBlank(deviceId) || StringUtils.isBlank(requestId)) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "deviceId、requestId 必填");
        }
        DeviceEntity device = deviceDao.selectById(deviceId.trim());
        if (device == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "设备不存在");
        }
        String clientId = buildMqttClientId(device);
        String uploadToken = UUID.randomUUID().toString().replace("-", "");
        String uploadUrl = buildUploadUrl(uploadBaseUrl, requestId.trim());
        ParentSnapshotPendingDTO pending = new ParentSnapshotPendingDTO();
        pending.setRequestId(requestId.trim());
        pending.setDeviceId(device.getId());
        pending.setClientId(clientId);
        pending.setUploadToken(uploadToken);
        pending.setTaskType(TASK_TYPE_PARENT_SNAPSHOT);
        pending.setStatus("waiting");
        savePending(pending);
        redisUtils.set(RedisKeys.getParentSnapshotTokenKey(uploadToken), pending.getRequestId(), PENDING_TTL_SECONDS);
        ParentSnapshotPrepareVO vo = new ParentSnapshotPrepareVO();
        vo.setRequestId(pending.getRequestId());
        vo.setClientId(clientId);
        vo.setUploadToken(uploadToken);
        vo.setUploadUrl(uploadUrl);
        vo.setTaskType(TASK_TYPE_PARENT_SNAPSHOT);
        return vo;
    }

    @Override
    public void deviceUpload(String requestId, String uploadToken, byte[] bytes, String mimeType, Integer width,
            Integer height, String taskType) {
        if (StringUtils.isBlank(requestId) || StringUtils.isBlank(uploadToken) || bytes == null || bytes.length == 0) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        ParentSnapshotPendingDTO pending = loadPending(requestId.trim());
        if (pending == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "快照请求不存在或已过期");
        }
        if (!uploadToken.trim().equals(pending.getUploadToken())) {
            throw new RenException(ErrorCode.UNAUTHORIZED, "uploadToken 无效");
        }
        if ("uploaded".equals(pending.getStatus())) {
            return;
        }
        if (StringUtils.isNotBlank(taskType)
                && StringUtils.isNotBlank(pending.getTaskType())
                && !taskType.trim().equals(pending.getTaskType())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "taskType 不匹配");
        }
        String mime = StringUtils.defaultIfBlank(mimeType, "image/jpeg");
        String ext = "jpg";
        if (mime.contains("png")) {
            ext = "png";
        } else if (mime.contains("webp")) {
            ext = "webp";
        }
        ParentStorageUploadVO upload = parentStorageService.uploadBase64(
                ParentStorageCategory.CHAT_SNAPSHOT, PENDING_PARENT_USER_ID, bytes, mime, ext);
        pending.setStatus("uploaded");
        pending.setObjectKey(upload.getObjectKey());
        pending.setAccessUrl(upload.getAccessUrl());
        pending.setWidth(width);
        pending.setHeight(height);
        savePending(pending);
        log.info("远程看娃设备上传成功 requestId={} deviceId={} bytes={}", requestId, pending.getDeviceId(), bytes.length);
    }

    @Override
    public ParentSnapshotStatusVO getStatus(String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return new ParentSnapshotStatusVO("not_found", null, null, null, null);
        }
        ParentSnapshotPendingDTO pending = loadPending(requestId.trim());
        if (pending == null) {
            return new ParentSnapshotStatusVO("not_found", null, null, null, null);
        }
        return new ParentSnapshotStatusVO(
                pending.getStatus(),
                pending.getObjectKey(),
                pending.getAccessUrl(),
                pending.getWidth(),
                pending.getHeight());
    }

    @Override
    public ParentChatSnapshotUploadResultVO finalizeSnapshot(String requestId, Long parentUserId, Long childId,
            Long assistantMessageId) {
        if (StringUtils.isBlank(requestId) || parentUserId == null || childId == null || assistantMessageId == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        ParentSnapshotPendingDTO pending = loadPending(requestId.trim());
        if (pending == null) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "快照请求不存在或已过期");
        }
        if (!"uploaded".equals(pending.getStatus())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "设备尚未上传画面");
        }
        ParentChatHistoryEntity msg = parentChatHistoryDao.selectById(assistantMessageId);
        if (msg == null || !parentUserId.equals(msg.getParentUserId()) || !childId.equals(msg.getChildId())
                || msg.getChatType() == null || msg.getChatType() != CHAT_TYPE_ASSISTANT) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "消息不存在");
        }
        if (StringUtils.isNotBlank(msg.getSnapshotRequestId())
                && !requestId.trim().equals(msg.getSnapshotRequestId())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "requestId 不匹配");
        }
        msg.setImageObjectKey(pending.getObjectKey());
        if (StringUtils.isBlank(msg.getMessageKind()) || "text".equals(msg.getMessageKind())) {
            msg.setMessageKind("text_with_snapshot");
        }
        parentChatHistoryDao.updateById(msg);
        clearPending(requestId.trim(), pending.getUploadToken());
        return new ParentChatSnapshotUploadResultVO(msg.getId(), pending.getObjectKey(), pending.getAccessUrl());
    }

    private String buildMqttClientId(DeviceEntity device) {
        String macAddress = Optional.ofNullable(device.getMacAddress()).orElse("unknown").replace(":", "_");
        String groupId = Optional.ofNullable(device.getBoard()).orElse("GID_default").replace(":", "_");
        return StrUtil.format("{}@@@{}@@@{}", groupId, macAddress, macAddress);
    }

    private String buildUploadUrl(String uploadBaseUrl, String requestId) {
        String base = StringUtils.isNotBlank(uploadBaseUrl) ? trimSlash(uploadBaseUrl.trim())
                : trimSlash(parentPublicBaseUrl);
        if (StringUtils.isBlank(base)) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "未配置 uploadBaseUrl 或 xiaozhi.parent.public-base-url");
        }
        String url = base + "/parent-api/chat/snapshot/device-upload";
        if (StringUtils.isNotBlank(requestId)) {
            url += "?requestId=" + requestId.trim();
        }
        return url;
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        String s = url.trim();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private void savePending(ParentSnapshotPendingDTO pending) {
        redisUtils.set(RedisKeys.getParentSnapshotPendingKey(pending.getRequestId()), pending, PENDING_TTL_SECONDS);
    }

    private ParentSnapshotPendingDTO loadPending(String requestId) {
        Object raw = redisUtils.get(RedisKeys.getParentSnapshotPendingKey(requestId));
        if (raw instanceof ParentSnapshotPendingDTO pending) {
            return pending;
        }
        if (raw instanceof Map<?, ?> map) {
            return JsonUtils.parseObject(JsonUtils.toJsonString(map), ParentSnapshotPendingDTO.class);
        }
        return null;
    }

    private void clearPending(String requestId, String uploadToken) {
        redisUtils.delete(java.util.List.of(
                RedisKeys.getParentSnapshotPendingKey(requestId),
                RedisKeys.getParentSnapshotTokenKey(uploadToken)));
    }
}
