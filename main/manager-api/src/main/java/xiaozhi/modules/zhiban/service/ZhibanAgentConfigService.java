package xiaozhi.modules.zhiban.service;

import xiaozhi.modules.zhiban.dto.ZhibanAgentConfigDTO;
import xiaozhi.modules.zhiban.vo.ZhibanAgentRuntimeVO;

public interface ZhibanAgentConfigService {

    ZhibanAgentConfigDTO getAdminConfig();

    void saveAdminConfig(ZhibanAgentConfigDTO dto);

    ZhibanAgentRuntimeVO getRuntimeConfig();
}
