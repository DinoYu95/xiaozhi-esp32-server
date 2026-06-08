package xiaozhi.modules.parent.beta.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.beta.dto.BetaMissionAdminConfigSaveDTO;
import xiaozhi.modules.parent.beta.dto.BetaMissionContextDTO;
import xiaozhi.modules.parent.beta.vo.BetaMissionAdminConfigVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionEntryStatusVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionFunnelVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionOverviewVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionUserDetailVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionUserProgressVO;

public interface BetaMissionService {

    BetaMissionEntryStatusVO getEntryStatus(Long parentUserId);

    void assertBetaMissionAllowed(Long parentUserId);

    BetaMissionOverviewVO getOverview(Long parentUserId);

    BetaMissionOverviewVO sync(Long parentUserId);

    BetaMissionOverviewVO setContext(Long parentUserId, BetaMissionContextDTO dto);

    BetaMissionOverviewVO skipStep(Long parentUserId, String stepKey);

    void visitStep(Long parentUserId, String stepKey);

    void dismissPopup(Long parentUserId);

    BetaMissionAdminConfigVO adminGetConfig();

    void adminSaveConfig(BetaMissionAdminConfigSaveDTO dto);

    BetaMissionFunnelVO adminFunnel();

    PageData<BetaMissionUserProgressVO> adminUsers(Map<String, Object> params);

    BetaMissionUserDetailVO adminUserDetail(Long parentUserId);
}
