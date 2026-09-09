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
import xiaozhi.modules.parent.dto.MemberVoicePrintSaveDTO;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.service.ParentDeviceChildVoicePrintService;
import xiaozhi.modules.parent.util.DeviceFamilyRole;
import xiaozhi.modules.parent.util.ParentDeviceAccessHelper;
import xiaozhi.modules.parent.vo.MemberVoicePrintContextVO;

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
        rejectAdultRoleSourceName(parentUserId, dto.getDeviceId(), dto.getSourceName());
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
    public MemberVoicePrintContextVO getMemberVoicePrintContext(Long parentUserId, String deviceId) {
        ParentDeviceBindingEntity binding = requireActiveBinding(parentUserId, deviceId);
        String resolvedDeviceId = binding.getDeviceId();
        DeviceEntity device = resolveDevice(resolvedDeviceId);
        MemberVoicePrintContextVO vo = new MemberVoicePrintContextVO();
        String familyRole = binding.getFamilyRole();
        String familyRoleLabel = DeviceFamilyRole.resolveLabel(familyRole);
        boolean roleSet = StringUtils.isNotBlank(familyRoleLabel);
        vo.setFamilyRoleSet(roleSet);
        vo.setFamilyRole(familyRole);
        vo.setFamilyRoleLabel(familyRoleLabel);
        vo.setLockedSourceName(familyRoleLabel);
        vo.setRequireFamilyRoleFirst(!roleSet);
        vo.setCanManage(roleSet);
        vo.setHasVoicePrint(false);
        vo.setVoicePrintId(null);
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            return vo;
        }
        AgentVoicePrintEntity existing = agentVoicePrintDao.selectOne(
                new LambdaQueryWrapper<AgentVoicePrintEntity>()
                        .eq(AgentVoicePrintEntity::getAgentId, device.getAgentId())
                        .eq(AgentVoicePrintEntity::getParentUserId, parentUserId));
        if (existing != null) {
            vo.setHasVoicePrint(true);
            vo.setVoicePrintId(existing.getId());
        }
        return vo;
    }

    @Override
    public void saveMemberVoicePrint(Long parentUserId, MemberVoicePrintSaveDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getDeviceId()) || StringUtils.isBlank(dto.getAudioId())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR);
        }
        ParentDeviceBindingEntity binding = requireActiveBinding(parentUserId, dto.getDeviceId());
        String familyRoleLabel = DeviceFamilyRole.resolveLabel(binding.getFamilyRole());
        if (StringUtils.isBlank(familyRoleLabel)) {
            throw new RenException(ErrorCode.PARENT_FAMILY_ROLE_REQUIRED);
        }
        DeviceEntity device = resolveDevice(binding.getDeviceId());
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            throw new RenException(ErrorCode.AGENT_NOT_FOUND);
        }
        agentVoicePrintService.saveMemberVoicePrint(
                device.getAgentId(),
                parentUserId,
                dto.getAudioId(),
                familyRoleLabel,
                dto.getIntroduce());
    }

    @Override
    public List<ParentDeviceVoicePrintVO> listVoicePrint(Long parentUserId, String deviceId) {
        ParentDeviceBindingEntity viewerBinding = requireActiveBinding(parentUserId, deviceId);
        String resolvedDeviceId = viewerBinding.getDeviceId();
        boolean viewerIsOwner = ParentDeviceAccessHelper.isOwner(viewerBinding);
        DeviceEntity device = resolveDevice(resolvedDeviceId);
        if (device == null || StringUtils.isBlank(device.getAgentId())) {
            return List.of();
        }
        DeviceChildEntity child = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>().eq(DeviceChildEntity::getDeviceId, resolvedDeviceId));
        Long mainChildId = child != null ? child.getId() : null;
        List<AgentVoicePrintEntity> entities = agentVoicePrintService.listByAgentIdForDevice(
                device.getAgentId(), mainChildId);
        return entities.stream().map(e -> toListItem(e, parentUserId, viewerIsOwner, mainChildId))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteVoicePrint(Long parentUserId, String voicePrintId) {
        AgentVoicePrintEntity entity = agentVoicePrintService.getById(voicePrintId);
        if (entity == null) {
            return;
        }
        if (entity.getChildId() != null) {
            DeviceChildEntity child = deviceChildDao.selectById(entity.getChildId());
            if (child == null) {
                agentVoicePrintService.deleteByVoicePrintId(voicePrintId);
                return;
            }
            ensureDeviceBoundToParent(parentUserId, child.getDeviceId());
            agentVoicePrintService.deleteByVoicePrintId(voicePrintId);
            return;
        }
        if (entity.getParentUserId() != null) {
            ensureCanManageMemberVoicePrint(parentUserId, entity);
            agentVoicePrintService.deleteByVoicePrintId(voicePrintId);
            return;
        }
        throw new RenException("后台录入声纹不可删除");
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
        requireActiveBinding(parentUserId, deviceId);
    }

    private ParentDeviceBindingEntity requireActiveBinding(Long parentUserId, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ParentDeviceBindingEntity binding = ParentDeviceAccessHelper.findActiveBinding(
                parentDeviceBindingDao, parentUserId, deviceId);
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        return binding;
    }

    private DeviceEntity resolveDevice(String deviceId) {
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null) {
            device = deviceDao.selectByIdOrMacVariant(deviceId);
        }
        return device;
    }

    private ParentDeviceVoicePrintVO toListItem(
            AgentVoicePrintEntity e, Long viewerParentUserId, boolean viewerIsOwner, Long mainChildId) {
        ParentDeviceVoicePrintVO vo = new ParentDeviceVoicePrintVO();
        vo.setId(e.getId());
        vo.setAudioId(e.getAudioId());
        vo.setSourceName(e.getSourceName());
        vo.setIntroduce(e.getIntroduce());
        vo.setCreateDate(e.getCreateDate());
        if (e.getChildId() != null) {
            vo.setVoicePrintType("child");
            vo.setCanManage(mainChildId != null && mainChildId.equals(e.getChildId()));
            vo.setMine(false);
        } else if (e.getParentUserId() != null) {
            vo.setVoicePrintType("member");
            vo.setParentUserId(e.getParentUserId());
            boolean mine = viewerParentUserId != null && viewerParentUserId.equals(e.getParentUserId());
            vo.setMine(mine);
            vo.setCanManage(mine || viewerIsOwner);
        } else {
            vo.setVoicePrintType("admin");
            vo.setCanManage(false);
            vo.setMine(false);
        }
        return vo;
    }

    /**
     * 已设置家庭角色的家长，不得用「孩子声纹」接口录入爸爸/妈妈等成人身份。
     */
    private void rejectAdultRoleSourceName(Long parentUserId, String deviceId, String sourceName) {
        if (StringUtils.isBlank(sourceName)) {
            return;
        }
        ParentDeviceBindingEntity binding = ParentDeviceAccessHelper.findActiveBinding(
                parentDeviceBindingDao, parentUserId, deviceId);
        if (binding == null || StringUtils.isBlank(binding.getFamilyRole())) {
            return;
        }
        String lockedLabel = DeviceFamilyRole.resolveLabel(binding.getFamilyRole());
        if (lockedLabel != null && lockedLabel.equals(sourceName.trim())) {
            throw new RenException(ErrorCode.PARENT_VOICEPRINT_ROLE_MISMATCH);
        }
    }

    private void ensureCanManageMemberVoicePrint(Long viewerParentUserId, AgentVoicePrintEntity entity) {
        if (entity.getParentUserId() == null) {
            throw new RenException("无权删除该声纹");
        }
        if (viewerParentUserId.equals(entity.getParentUserId())) {
            return;
        }
        List<DeviceEntity> devices = deviceDao.selectList(
                new LambdaQueryWrapper<DeviceEntity>().eq(DeviceEntity::getAgentId, entity.getAgentId()));
        for (DeviceEntity device : devices) {
            ParentDeviceBindingEntity ownerBinding = ParentDeviceAccessHelper.findActiveBinding(
                    parentDeviceBindingDao, viewerParentUserId, device.getId());
            if (ownerBinding != null && ParentDeviceAccessHelper.isOwner(ownerBinding)) {
                return;
            }
        }
        throw new RenException("无权删除该声纹");
    }
}
