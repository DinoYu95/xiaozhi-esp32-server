package xiaozhi.modules.parent.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.service.ParentUserAdminService;
import xiaozhi.modules.parent.vo.AdminParentUserDetailVO;
import xiaozhi.modules.parent.vo.AdminParentUserListItemVO;

@RestController
@RequestMapping("admin/parent-user")
@RequiredArgsConstructor
@Tag(name = "管理端-家长用户")
public class ParentUserAdminController {

    private final ParentUserAdminService parentUserAdminService;

    @GetMapping("/page")
    @Operation(summary = "家长用户分页")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<PageData<AdminParentUserListItemVO>> page(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        return new Result<PageData<AdminParentUserListItemVO>>().ok(parentUserAdminService.adminPage(params));
    }

    @GetMapping("/{parentUserId}")
    @Operation(summary = "家长用户详情")
    @RequiresPermissions("sys:role:superAdmin")
    public Result<AdminParentUserDetailVO> detail(@PathVariable Long parentUserId) {
        return new Result<AdminParentUserDetailVO>().ok(parentUserAdminService.adminDetail(parentUserId));
    }
}
