package xiaozhi.modules.parent.service.impl;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.agent.entity.AgentVoicePrintEntity;
import xiaozhi.modules.agent.service.AgentChatAudioService;
import xiaozhi.modules.agent.service.AgentVoicePrintService;
import xiaozhi.modules.agent.vo.AgentVoicePrintVO;
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

    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final DeviceChildDao deviceChildDao;
    private final DeviceDao deviceDao;
    private final AgentChatAudioService agentChatAudioService;
    private final AgentVoicePrintService agentVoicePrintService;

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
    public List<AgentVoicePrintVO> listVoicePrint(Long parentUserId, String deviceId) {
        ensureDeviceBoundToParent(parentUserId, deviceId);
        DeviceChildEntity child = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>().eq(DeviceChildEntity::getDeviceId, deviceId));
        if (child == null) {
            return Collections.emptyList();
        }
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            return Collections.emptyList();
        }
        return agentVoicePrintService.listByAgentIdAndChildId(device.getAgentId(), child.getId());
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

    private void ensureDeviceBoundToParent(Long parentUserId, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getDeviceId, deviceId));
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
    }
}
