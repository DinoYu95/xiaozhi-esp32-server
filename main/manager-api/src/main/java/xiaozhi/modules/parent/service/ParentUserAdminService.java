package xiaozhi.modules.parent.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.vo.AdminParentUserDetailVO;
import xiaozhi.modules.parent.vo.AdminParentUserListItemVO;

public interface ParentUserAdminService {

    PageData<AdminParentUserListItemVO> adminPage(Map<String, Object> params);

    AdminParentUserDetailVO adminDetail(Long parentUserId);
}
