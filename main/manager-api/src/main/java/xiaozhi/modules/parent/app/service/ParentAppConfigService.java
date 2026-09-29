package xiaozhi.modules.parent.app.service;

import xiaozhi.modules.parent.app.dto.ParentAppAdminPublicConfigSaveDTO;
import xiaozhi.modules.parent.app.dto.ParentAppAdminSettingsSaveDTO;
import xiaozhi.modules.parent.app.vo.ParentAppAdminSettingsVO;
import xiaozhi.modules.parent.app.vo.ParentAppGuestPreviewVO;
import xiaozhi.modules.parent.app.vo.ParentAppPublicConfigVO;

public interface ParentAppConfigService {

    ParentAppPublicConfigVO getPublicConfig();

    ParentAppGuestPreviewVO getGuestPreview();

    String getAccessMode();

    boolean isReviewMode();

    ParentAppAdminSettingsVO adminGetSettings();

    void adminSaveSettings(ParentAppAdminSettingsSaveDTO dto);

    void adminSavePublicConfig(ParentAppAdminPublicConfigSaveDTO dto);
}
