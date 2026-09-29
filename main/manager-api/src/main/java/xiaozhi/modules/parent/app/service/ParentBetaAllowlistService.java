package xiaozhi.modules.parent.app.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.app.dto.ParentBetaAllowlistSaveDTO;
import xiaozhi.modules.parent.app.vo.ParentBetaAllowlistVO;

public interface ParentBetaAllowlistService {

    PageData<ParentBetaAllowlistVO> adminPage(Map<String, Object> params);

    void adminSave(ParentBetaAllowlistSaveDTO dto);

    void adminDelete(Long id);
}
