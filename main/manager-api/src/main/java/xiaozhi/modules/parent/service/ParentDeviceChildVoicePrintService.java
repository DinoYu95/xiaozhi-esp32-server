package xiaozhi.modules.parent.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import xiaozhi.modules.agent.vo.AgentVoicePrintVO;
import xiaozhi.modules.parent.dto.ChildVoicePrintSaveDTO;

/**
 * 家长端-设备主孩子声纹
 */
public interface ParentDeviceChildVoicePrintService {

    /**
     * 上传孩子声纹音频（WAV），返回 audioId 供后续保存声纹使用
     *
     * @param parentUserId 当前家长用户ID
     * @param deviceId     设备ID
     * @param file         WAV 文件
     * @return ai_agent_chat_audio 的 id
     */
    String uploadAudio(Long parentUserId, String deviceId, MultipartFile file);

    /**
     * 添加或更新主孩子声纹（一孩一声纹）
     *
     * @param parentUserId 当前家长用户ID
     * @param dto          声纹信息
     */
    void saveVoicePrint(Long parentUserId, ChildVoicePrintSaveDTO dto);

    /**
     * 查询该设备主孩子在该设备智能体下的声纹（0 或 1 条）
     *
     * @param parentUserId 当前家长用户ID
     * @param deviceId     设备ID
     * @return 声纹列表
     */
    List<AgentVoicePrintVO> listVoicePrint(Long parentUserId, String deviceId);

    /**
     * 删除主孩子声纹
     *
     * @param parentUserId  当前家长用户ID
     * @param voicePrintId  声纹ID
     */
    void deleteVoicePrint(Long parentUserId, String voicePrintId);
}
