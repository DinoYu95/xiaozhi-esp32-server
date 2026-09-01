package xiaozhi.modules.ota.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
    @Operation(summary = "下载 SWU（本地磁盘或 OSS 代理）")
    public ResponseEntity<StreamingResponseBody> downloadSwu(
            @org.springframework.web.bind.annotation.PathVariable("ossKey") String ossKey) {
        if (ossKey != null && ossKey.startsWith("/")) {
            ossKey = ossKey.substring(1);
        }
        OtaPackageStorageService.SwuStream stream = storageService.openSwuStream(ossKey);
        if (stream == null) {
            return ResponseEntity.notFound().build();
        }
        StreamingResponseBody body = outputStream -> {
            try (OtaPackageStorageService.SwuStream s = stream) {
                s.inputStream().transferTo(outputStream);
            }
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + stream.filename() + "\"");
        if (stream.contentLength() > 0) {
            builder.contentLength(stream.contentLength());
        }
        return builder.body(body);
    }
}
