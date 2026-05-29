package xiaozhi.modules.parent.service;

import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.dto.ParentFeedbackAdminNoteDTO;
import xiaozhi.modules.parent.dto.ParentFeedbackAdminStatusDTO;
import xiaozhi.modules.parent.dto.ParentFeedbackCreateDTO;
import xiaozhi.modules.parent.vo.ParentFeedbackAdminVO;
import xiaozhi.modules.parent.vo.ParentFeedbackDetailVO;
import xiaozhi.modules.parent.vo.ParentFeedbackEnabledVO;
import xiaozhi.modules.parent.vo.ParentFeedbackVO;

public interface ParentFeedbackService {

    ParentFeedbackEnabledVO getEntryStatus(Long parentUserId);

    void assertBetaFeedbackAllowed(Long parentUserId);

    ParentFeedbackVO create(Long parentUserId, ParentFeedbackCreateDTO dto);

    String storeFeedbackImage(MultipartFile file);

    PageData<ParentFeedbackVO> pageByParent(Long parentUserId, int page, int limit);

    ParentFeedbackDetailVO getByParent(Long parentUserId, Long id);

    PageData<ParentFeedbackAdminVO> adminPage(Map<String, Object> params);

    ParentFeedbackAdminVO adminGet(Long id);

    void adminUpdateStatus(Long id, ParentFeedbackAdminStatusDTO dto);

    void adminUpdateNote(Long id, ParentFeedbackAdminNoteDTO dto);

    void adminSetBetaTester(Long parentUserId, boolean betaTester);
}
