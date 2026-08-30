package xiaozhi.modules.ota.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.ota.dto.DeviceOtaCheckReqDTO;
import xiaozhi.modules.ota.dto.DeviceOtaReportDTO;
import xiaozhi.modules.ota.service.DevopsOtaService;
import xiaozhi.modules.ota.service.OtaPackageStorageService;
import xiaozhi.modules.ota.vo.DeviceOtaCheckRespVO;

@Tag(name = "设备端硬件 OTA")
@RestController
@RequiredArgsConstructor
@RequestMapping("/ota")
public class DeviceOtaController {

    private final DevopsOtaService devopsOtaService;
    private final OtaPackageStorageService storageService;

    @PostMapping("/check")
    @Operation(summary = "设备拉取 SWU manifest")
    public Result<DeviceOtaCheckRespVO> check(@Valid @RequestBody DeviceOtaCheckReqDTO req) {
        Result<DeviceOtaCheckRespVO> result = new Result<DeviceOtaCheckRespVO>().ok(devopsOtaService.checkManifest(req));
        result.setMsg("ok");
        return result;
    }

    @PostMapping("/report")
    @Operation(summary = "设备上报升级结果")
    public Result<Void> report(@Valid @RequestBody DeviceOtaReportDTO dto) {
        devopsOtaService.reportUpgrade(dto);
        Result<Void> result = new Result<Void>().ok(null);
        result.setMsg("ok");
        return result;
    }

    @GetMapping("/swu/file/{*ossKey}")
    @Operation(summary = "本地回退下载 SWU（OSS 未启用时）")
    public ResponseEntity<byte[]> downloadLocal(@org.springframework.web.bind.annotation.PathVariable("ossKey") String ossKey) {
        if (ossKey != null && ossKey.startsWith("/")) {
            ossKey = ossKey.substring(1);
        }
        byte[] bytes = storageService.readLocalFile(ossKey);
        if (bytes == null) {
            return ResponseEntity.notFound().build();
        }
        String filename = ossKey.contains("/") ? ossKey.substring(ossKey.lastIndexOf('/') + 1) : ossKey;
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(bytes);
    }
}
