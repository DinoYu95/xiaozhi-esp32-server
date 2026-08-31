package xiaozhi.modules.ota.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.ota.dto.HardwareTypeCreateDTO;
import xiaozhi.modules.ota.dto.HardwareTypeUpdateDTO;
import xiaozhi.modules.ota.dto.PackageRegisterDTO;
import xiaozhi.modules.ota.dto.PoolDevicesAddDTO;
import xiaozhi.modules.ota.dto.ReleaseCreateDTO;
import xiaozhi.modules.ota.dto.ReleaseRollbackDTO;
import xiaozhi.modules.ota.dto.ReleaseRolloutUpdateDTO;
import xiaozhi.modules.ota.dto.WhitelistPoolCreateDTO;
import xiaozhi.modules.ota.dto.WhitelistPoolUpdateDTO;
import xiaozhi.modules.ota.service.DevopsOtaService;
import xiaozhi.modules.ota.vo.DevicesListVO;
import xiaozhi.modules.ota.vo.HardwareTypeVO;
import xiaozhi.modules.ota.vo.PackageVO;
import xiaozhi.modules.ota.vo.ReleaseCoverageDetailVO;
import xiaozhi.modules.ota.vo.ReleaseVO;
import xiaozhi.modules.ota.vo.WhitelistPoolVO;

@Tag(name = "DevOps 硬件 OTA")
@RestController
@RequiredArgsConstructor
@RequestMapping("/devops/ota")
public class DevopsOtaController {

    private final DevopsOtaService devopsOtaService;

    @GetMapping("/hardware-types")
    @Operation(summary = "硬件类型列表")
    public Result<List<HardwareTypeVO>> listHardwareTypes() {
        return ok(devopsOtaService.listHardwareTypes());
    }

    @PostMapping("/hardware-types")
    @Operation(summary = "创建硬件类型")
    public Result<HardwareTypeVO> createHardwareType(@Valid @RequestBody HardwareTypeCreateDTO dto) {
        return ok(devopsOtaService.createHardwareType(dto));
    }

    @PutMapping("/hardware-types/{key}")
    @Operation(summary = "更新硬件类型")
    public Result<HardwareTypeVO> updateHardwareType(@PathVariable("key") String key,
            @RequestBody HardwareTypeUpdateDTO dto) {
        return ok(devopsOtaService.updateHardwareType(key, dto));
    }

    @DeleteMapping("/hardware-types/{key}")
    @Operation(summary = "软删除硬件类型（enabled=false）")
    public Result<Void> deleteHardwareType(@PathVariable("key") String key) {
        devopsOtaService.deleteHardwareType(key);
        return ok(null);
    }

    @PostMapping("/admin/reset")
    @Operation(summary = "清空全部 OTA 数据（包/发布/白名单/硬件类型），保留 ai_device")
    public Result<Void> resetAllOtaData() {
        devopsOtaService.resetAllOtaData();
        return ok(null);
    }

    @GetMapping("/packages")
    @Operation(summary = "SWU 包列表")
    public Result<List<PackageVO>> listPackages(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "hardware", required = false) String hardware,
            @RequestParam(value = "channel", required = false) String channel,
            @RequestParam(value = "status", required = false) String status) {
        return ok(devopsOtaService.listPackages(type, hardware, channel, status));
    }

    @PostMapping("/packages/upload")
    @Operation(summary = "上传 SWU 包到 OSS")
    public Result<PackageVO> uploadPackage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestHeader(value = "X-DevOps-User", required = false) String user) {
        return ok(devopsOtaService.uploadPackage(file, notes, user));
    }

    @PostMapping("/packages/register")
    @Operation(summary = "登记已上传 OSS 的 SWU 包 metadata")
    public Result<PackageVO> registerPackage(@Valid @RequestBody PackageRegisterDTO dto,
            @RequestHeader(value = "X-DevOps-User", required = false) String user) {
        return ok(devopsOtaService.registerPackage(dto, user));
    }

    @DeleteMapping("/packages/{id}")
    @Operation(summary = "删除 draft 包")
    public Result<Void> deletePackage(@PathVariable("id") String id) {
        devopsOtaService.deletePackage(id);
        return ok(null);
    }

    @GetMapping("/devices")
    @Operation(summary = "DevOps 设备列表")
    public Result<DevicesListVO> listDevices(
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "page_size", required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "hardware", required = false) String hardware,
            @RequestParam(value = "device_type", required = false) String deviceType,
            @RequestParam(value = "channel", required = false) String channel) {
        return ok(devopsOtaService.listDevices(page, pageSize, keyword, hardware, deviceType, channel));
    }

    @GetMapping("/whitelist-pools")
    @Operation(summary = "白名单池列表")
    public Result<List<WhitelistPoolVO>> listPools() {
        return ok(devopsOtaService.listPools());
    }

    @PostMapping("/whitelist-pools")
    @Operation(summary = "创建白名单池")
    public Result<WhitelistPoolVO> createPool(@Valid @RequestBody WhitelistPoolCreateDTO dto) {
        return ok(devopsOtaService.createPool(dto));
    }

    @PutMapping("/whitelist-pools/{id}")
    @Operation(summary = "更新白名单池")
    public Result<WhitelistPoolVO> updatePool(@PathVariable("id") Long id,
            @RequestBody WhitelistPoolUpdateDTO dto) {
        return ok(devopsOtaService.updatePool(id, dto));
    }

    @DeleteMapping("/whitelist-pools/{id}")
    @Operation(summary = "删除白名单池")
    public Result<Void> deletePool(@PathVariable("id") Long id) {
        devopsOtaService.deletePool(id);
        return ok(null);
    }

    @PostMapping("/whitelist-pools/{id}/devices")
    @Operation(summary = "批量添加白名单设备")
    public Result<WhitelistPoolVO> addPoolDevices(@PathVariable("id") Long id,
            @Valid @RequestBody PoolDevicesAddDTO dto) {
        return ok(devopsOtaService.addPoolDevices(id, dto));
    }

    @DeleteMapping("/whitelist-pools/{id}/devices/{mac}")
    @Operation(summary = "移除白名单设备")
    public Result<Void> removePoolDevice(@PathVariable("id") Long id, @PathVariable("mac") String mac) {
        devopsOtaService.removePoolDevice(id, mac);
        return ok(null);
    }

    @GetMapping("/releases")
    @Operation(summary = "发布列表")
    public Result<List<ReleaseVO>> listReleases() {
        return ok(devopsOtaService.listReleases());
    }

    @PostMapping("/releases")
    @Operation(summary = "创建发布")
    public Result<ReleaseVO> createRelease(@Valid @RequestBody ReleaseCreateDTO dto,
            @RequestHeader(value = "X-DevOps-User", required = false) String user) {
        return ok(devopsOtaService.createRelease(dto, user));
    }

    @PutMapping("/releases/{id}/rollout")
    @Operation(summary = "调整 active 发布灰度比例（不新建发布）")
    public Result<ReleaseVO> updateReleaseRollout(@PathVariable("id") Long id,
            @Valid @RequestBody ReleaseRolloutUpdateDTO dto) {
        return ok(devopsOtaService.updateReleaseRollout(id, dto));
    }

    @PostMapping("/releases/{id}/rollback")
    @Operation(summary = "Beta 回滚")
    public Result<ReleaseVO> rollback(@PathVariable("id") Long id,
            @RequestBody(required = false) ReleaseRollbackDTO dto) {
        return ok(devopsOtaService.rollbackRelease(id, dto == null ? new ReleaseRollbackDTO() : dto));
    }

    @GetMapping("/releases/{id}/coverage")
    @Operation(summary = "发布覆盖度")
    public Result<ReleaseCoverageDetailVO> coverage(@PathVariable("id") Long id) {
        return ok(devopsOtaService.coverage(id));
    }

    private static <T> Result<T> ok(T data) {
        Result<T> result = new Result<T>().ok(data);
        result.setMsg("ok");
        return result;
    }
}
