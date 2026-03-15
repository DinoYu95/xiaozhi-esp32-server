package xiaozhi.modules.parent.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.modules.agent.dao.AgentVoicePrintDao;
import xiaozhi.modules.agent.entity.AgentVoicePrintEntity;
import xiaozhi.modules.agent.service.AgentChatAudioService;
import xiaozhi.modules.agent.service.AgentVoicePrintService;
import xiaozhi.modules.parent.vo.ParentDeviceVoicePrintVO;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dto.ChildVoicePrintSaveDTO;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.service.ParentDeviceChildVoicePrintService;

@Service
@RequiredArgsConstructor
public class ParentDeviceChildVoicePrintServiceImpl implements ParentDeviceChildVoicePrintService {

    /** 播放 token 有效时长（秒），一次性使用后即删 */
    private static final int PLAY_TOKEN_EXPIRE_SECONDS = 300;

    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final DeviceChildDao deviceChildDao;
    private final DeviceDao deviceDao;
    private final AgentVoicePrintDao agentVoicePrintDao;
    private final AgentChatAudioService agentChatAudioService;
    private final AgentVoicePrintService agentVoicePrintService;
    private final RedisUtils redisUtils;

    @Override
    public String uploadAudio(Long parentUserId, String deviceId, MultipartFile file) {
        ensureDeviceBoundToParent(parentUserId, deviceId);
        DeviceChildEntity child = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>().eq(DeviceChildEntity::getDeviceId, deviceId));
        if (child == null) {
            throw new RenException("请先添加设备主孩子");
        }
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.VOICEPRINT_AUDIO_EMPTY);
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        boolean contentTypeOk = contentType != null
                && (contentType.toLowerCase().contains("audio") || contentType.toLowerCase().contains("wav"));
        boolean filenameOk = filename != null && filename.toLowerCase().endsWith(".wav");
        if (!contentTypeOk && !filenameOk) {
            throw new RenException("请上传 WAV 等音频格式");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new RenException("音频文件不超过 10MB");
        }
        try {
            byte[] bytes = file.getBytes();
            return agentChatAudioService.saveAudio(bytes);
        } catch (IOException e) {
            throw new RenException("读取音频失败");
        }
    }

    @Override
    public void saveVoicePrint(Long parentUserId, ChildVoicePrintSaveDTO dto) {
        ensureDeviceBoundToParent(parentUserId, dto.getDeviceId());
        DeviceChildEntity child = deviceChildDao.selectById(dto.getChildId());
        if (child == null || !child.getDeviceId().equals(dto.getDeviceId())) {
            throw new RenException("孩子与设备不匹配");
        }
        DeviceEntity device = deviceDao.selectById(dto.getDeviceId());
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            throw new RenException(ErrorCode.AGENT_NOT_FOUND);
        }
        agentVoicePrintService.saveChildVoicePrint(
                device.getAgentId(),
                dto.getChildId(),
                dto.getAudioId(),
                dto.getSourceName(),
                dto.getIntroduce());
    }

    @Override
    public List<ParentDeviceVoicePrintVO> listVoicePrint(Long parentUserId, String deviceId) {
        ensureDeviceBoundToParent(parentUserId, deviceId);
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null) {
            device = deviceDao.selectByIdOrMacVariant(deviceId);
        }
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            return List.of();
        }
        DeviceChildEntity child = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>().eq(DeviceChildEntity::getDeviceId, deviceId));
        Long mainChildId = child != null ? child.getId() : null;
        List<AgentVoicePrintEntity> entities = agentVoicePrintService.listByAgentIdForDevice(
                device.getAgentId(), mainChildId);
        return entities.stream().map(e -> {
            ParentDeviceVoicePrintVO vo = new ParentDeviceVoicePrintVO();
            vo.setId(e.getId());
            vo.setAudioId(e.getAudioId());
            vo.setSourceName(e.getSourceName());
            vo.setIntroduce(e.getIntroduce());
            vo.setCreateDate(e.getCreateDate());
            vo.setCanManage(mainChildId != null && mainChildId.equals(e.getChildId()));
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public void deleteVoicePrint(Long parentUserId, String voicePrintId) {
        AgentVoicePrintEntity entity = agentVoicePrintService.getById(voicePrintId);
        if (entity == null) {
            return;
        }
        if (entity.getChildId() == null) {
            throw new RenException("仅可删除主孩子声纹");
        }
        DeviceChildEntity child = deviceChildDao.selectById(entity.getChildId());
        if (child == null) {
            agentVoicePrintService.deleteByVoicePrintId(voicePrintId);
            return;
        }
        ensureDeviceBoundToParent(parentUserId, child.getDeviceId());
        agentVoicePrintService.deleteByVoicePrintId(voicePrintId);
    }

    @Override
    public String getPlayToken(Long parentUserId, String audioId) {
        if (StringUtils.isBlank(audioId)) {
            throw new RenException("音频ID不能为空");
        }
        AgentVoicePrintEntity vp = agentVoicePrintDao.selectOne(
                new LambdaQueryWrapper<AgentVoicePrintEntity>().eq(AgentVoicePrintEntity::getAudioId, audioId));
        if (vp == null) {
            throw new RenException("音频不存在或无权访问");
        }
        List<DeviceEntity> devices = deviceDao.selectList(
                new LambdaQueryWrapper<DeviceEntity>().eq(DeviceEntity::getAgentId, vp.getAgentId()));
        boolean canAccess = false;
        for (DeviceEntity d : devices) {
            ParentDeviceBindingEntity b = parentDeviceBindingDao.selectOne(
                    new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                            .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                            .eq(ParentDeviceBindingEntity::getDeviceId, d.getId()));
            if (b != null) {
                canAccess = true;
                break;
            }
        }
        if (!canAccess) {
            throw new RenException("音频不存在或无权访问");
        }
        byte[] audio = agentChatAudioService.getAudio(audioId);
        if (audio == null || audio.length == 0) {
            throw new RenException("音频不存在");
        }
        String uuid = UUID.randomUUID().toString();
        redisUtils.set(RedisKeys.getParentVoicePrintAudioKey(uuid), audioId, PLAY_TOKEN_EXPIRE_SECONDS);
        return uuid;
    }

    @Override
    public byte[] getAudioByPlayToken(String playToken) {
        if (StringUtils.isBlank(playToken)) return null;
        String audioId = (String) redisUtils.get(RedisKeys.getParentVoicePrintAudioKey(playToken));
        if (StringUtils.isBlank(audioId)) return null;
        redisUtils.delete(List.of(RedisKeys.getParentVoicePrintAudioKey(playToken)));
        return agentChatAudioService.getAudio(audioId);
    }

    private void ensureDeviceBoundToParent(Long parentUserId, String deviceId) {
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
}
