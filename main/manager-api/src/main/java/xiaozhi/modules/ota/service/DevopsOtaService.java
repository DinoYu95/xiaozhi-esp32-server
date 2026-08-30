package xiaozhi.modules.ota.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.ota.dto.DeviceOtaCheckReqDTO;
import xiaozhi.modules.ota.dto.DeviceOtaReportDTO;
import xiaozhi.modules.ota.dto.HardwareTypeCreateDTO;
import xiaozhi.modules.ota.dto.HardwareTypeUpdateDTO;
import xiaozhi.modules.ota.dto.PoolDevicesAddDTO;
import xiaozhi.modules.ota.dto.ReleaseCreateDTO;
import xiaozhi.modules.ota.dto.ReleaseRollbackDTO;
import xiaozhi.modules.ota.dto.WhitelistPoolCreateDTO;
import xiaozhi.modules.ota.dto.WhitelistPoolUpdateDTO;
import xiaozhi.modules.ota.vo.DeviceOtaCheckRespVO;
import xiaozhi.modules.ota.vo.DevicesListVO;
import xiaozhi.modules.ota.vo.HardwareTypeVO;
import xiaozhi.modules.ota.vo.PackageVO;
import xiaozhi.modules.ota.vo.ReleaseCoverageDetailVO;
import xiaozhi.modules.ota.vo.ReleaseVO;
import xiaozhi.modules.ota.vo.WhitelistPoolVO;

public interface DevopsOtaService {

    List<HardwareTypeVO> listHardwareTypes();

    HardwareTypeVO createHardwareType(HardwareTypeCreateDTO dto);

    HardwareTypeVO updateHardwareType(String key, HardwareTypeUpdateDTO dto);

    void deleteHardwareType(String key);

    PackageVO uploadPackage(MultipartFile file, String notes, String createdBy);

    List<PackageVO> listPackages(String type, String hardware, String channel, String status);

    void deletePackage(String id);

    DevicesListVO listDevices(Integer page, Integer pageSize, String keyword, String hardware,
            String deviceType, String channel);

    List<WhitelistPoolVO> listPools();

    WhitelistPoolVO createPool(WhitelistPoolCreateDTO dto);

    WhitelistPoolVO updatePool(Long id, WhitelistPoolUpdateDTO dto);

    void deletePool(Long id);

    WhitelistPoolVO addPoolDevices(Long id, PoolDevicesAddDTO dto);

    void removePoolDevice(Long id, String mac);

    List<ReleaseVO> listReleases();

    ReleaseVO createRelease(ReleaseCreateDTO dto, String publishedBy);

    ReleaseVO rollbackRelease(Long id, ReleaseRollbackDTO dto);

    ReleaseCoverageDetailVO coverage(Long id);

    DeviceOtaCheckRespVO checkManifest(DeviceOtaCheckReqDTO req);

    DeviceOtaCheckRespVO checkManifestForDevice(DeviceEntity device);

    void reportUpgrade(DeviceOtaReportDTO dto);

    boolean isKnownHardware(String hardware);
}
