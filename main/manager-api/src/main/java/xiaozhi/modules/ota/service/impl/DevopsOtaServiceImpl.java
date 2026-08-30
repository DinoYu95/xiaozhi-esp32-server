package xiaozhi.modules.ota.service.impl;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.ota.dao.OtaDeviceUpgradeLogDao;
import xiaozhi.modules.ota.dao.OtaHardwareTypeDao;
import xiaozhi.modules.ota.dao.OtaPackageDao;
import xiaozhi.modules.ota.dao.OtaReleaseDao;
import xiaozhi.modules.ota.dao.OtaReleasePoolDao;
import xiaozhi.modules.ota.dao.OtaWhitelistPoolDao;
import xiaozhi.modules.ota.dao.OtaWhitelistPoolDeviceDao;
import xiaozhi.modules.ota.dto.DeviceOtaCheckReqDTO;
import xiaozhi.modules.ota.dto.DeviceOtaReportDTO;
import xiaozhi.modules.ota.dto.HardwareTypeCreateDTO;
import xiaozhi.modules.ota.dto.HardwareTypeUpdateDTO;
import xiaozhi.modules.ota.dto.PoolDevicesAddDTO;
import xiaozhi.modules.ota.dto.ReleaseCreateDTO;
import xiaozhi.modules.ota.dto.ReleaseRollbackDTO;
import xiaozhi.modules.ota.dto.WhitelistPoolCreateDTO;
import xiaozhi.modules.ota.dto.WhitelistPoolUpdateDTO;
import xiaozhi.modules.ota.entity.OtaDeviceUpgradeLogEntity;
import xiaozhi.modules.ota.entity.OtaHardwareTypeEntity;
import xiaozhi.modules.ota.entity.OtaPackageEntity;
import xiaozhi.modules.ota.entity.OtaReleaseEntity;
import xiaozhi.modules.ota.entity.OtaReleasePoolEntity;
import xiaozhi.modules.ota.entity.OtaWhitelistPoolDeviceEntity;
import xiaozhi.modules.ota.entity.OtaWhitelistPoolEntity;
import xiaozhi.modules.ota.service.DevopsOtaService;
import xiaozhi.modules.ota.service.OtaPackageStorageService;
import xiaozhi.modules.ota.util.OtaReleaseStateMachine;
import xiaozhi.modules.ota.util.OtaRolloutMatcher;
import xiaozhi.modules.ota.util.OtaVersionUtils;
import xiaozhi.modules.ota.util.SwuFilenameParser;
import xiaozhi.modules.ota.vo.CoverageDeviceVO;
import xiaozhi.modules.ota.vo.DeviceOtaCheckRespVO;
import xiaozhi.modules.ota.vo.DeviceOtaViewVO;
import xiaozhi.modules.ota.vo.DevicesListVO;
import xiaozhi.modules.ota.vo.HardwareTypeVO;
import xiaozhi.modules.ota.vo.PackageVO;
import xiaozhi.modules.ota.vo.ReleaseCoverageDetailVO;
import xiaozhi.modules.ota.vo.ReleaseCoverageVO;
import xiaozhi.modules.ota.vo.ReleaseVO;
import xiaozhi.modules.ota.vo.WhitelistPoolVO;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.util.ParentDeviceDisplayResolver;

@Slf4j
@Service
@RequiredArgsConstructor
public class DevopsOtaServiceImpl implements DevopsOtaService {

    private static final long ONLINE_WINDOW_MS = 10 * 60 * 1000L;
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final OtaHardwareTypeDao hardwareTypeDao;
    private final OtaPackageDao packageDao;
    private final OtaWhitelistPoolDao poolDao;
    private final OtaWhitelistPoolDeviceDao poolDeviceDao;
    private final OtaReleaseDao releaseDao;
    private final OtaReleasePoolDao releasePoolDao;
    private final OtaDeviceUpgradeLogDao upgradeLogDao;
    private final DeviceDao deviceDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final OtaPackageStorageService storageService;
    private final ObjectMapper objectMapper;

    @Override
    public List<HardwareTypeVO> listHardwareTypes() {
        return hardwareTypeDao.selectList(new LambdaQueryWrapper<OtaHardwareTypeEntity>()
                .orderByAsc(OtaHardwareTypeEntity::getHwKey)).stream().map(this::toHardwareVo).toList();
    }

    @Override
    @Transactional
    public HardwareTypeVO createHardwareType(HardwareTypeCreateDTO dto) {
        String key = dto.getKey().trim();
        if (hardwareTypeDao.selectById(key) != null) {
            throw new RenException("硬件类型 " + key + " 已存在");
        }
        Date now = new Date();
        OtaHardwareTypeEntity row = new OtaHardwareTypeEntity();
        row.setHwKey(key);
        row.setName(dto.getName().trim());
        row.setDescription(dto.getDescription());
        row.setEnabled(1);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        hardwareTypeDao.insert(row);
        return toHardwareVo(row);
    }

    @Override
    @Transactional
    public HardwareTypeVO updateHardwareType(String key, HardwareTypeUpdateDTO dto) {
        OtaHardwareTypeEntity row = requireHardware(key);
        if (dto.getName() != null) {
            row.setName(dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            row.setDescription(dto.getDescription());
        }
        if (dto.getEnabled() != null) {
            row.setEnabled(Boolean.TRUE.equals(dto.getEnabled()) ? 1 : 0);
        }
        row.setUpdatedAt(new Date());
        hardwareTypeDao.updateById(row);
        return toHardwareVo(row);
    }

    @Override
    @Transactional
    public void deleteHardwareType(String key) {
        OtaHardwareTypeEntity row = requireHardware(key);
        row.setEnabled(0);
        row.setUpdatedAt(new Date());
        hardwareTypeDao.updateById(row);
    }

    @Override
    @Transactional
    public PackageVO uploadPackage(MultipartFile file, String notes, String createdBy) {
        if (file == null || file.isEmpty()) {
            throw new RenException("上传文件不能为空");
        }
        SwuFilenameParser.ParsedSwu parsed = SwuFilenameParser.parse(file.getOriginalFilename());
        OtaHardwareTypeEntity hw = hardwareTypeDao.selectById(parsed.hardware());
        if (hw == null || hw.getEnabled() == null || hw.getEnabled() == 0) {
            throw new RenException("硬件类型 " + parsed.hardware() + " 未配置或已禁用");
        }
        String sha256;
        try {
            sha256 = sha256Hex(file.getBytes());
            storageService.upload(parsed, file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new RenException("读取上传文件失败", e);
        }
        OtaPackageEntity pkg = new OtaPackageEntity();
        pkg.setType(parsed.type());
        pkg.setHardware(parsed.hardware());
        pkg.setVersion(parsed.version());
        pkg.setChannel(parsed.channel());
        pkg.setFilename(parsed.filename());
        pkg.setOssKey(SwuFilenameParser.ossKey(parsed));
        pkg.setSizeBytes(file.getSize());
        pkg.setSha256(sha256);
        pkg.setStatus("draft");
        pkg.setNotes(notes);
        pkg.setCreatedBy(StringUtils.defaultIfBlank(createdBy, "devops"));
        pkg.setCreatedAt(new Date());
        packageDao.insert(pkg);
        return toPackageVo(pkg);
    }

    @Override
    public List<PackageVO> listPackages(String type, String hardware, String channel, String status) {
        LambdaQueryWrapper<OtaPackageEntity> q = new LambdaQueryWrapper<>();
        q.eq(StringUtils.isNotBlank(type), OtaPackageEntity::getType, type);
        q.eq(StringUtils.isNotBlank(hardware), OtaPackageEntity::getHardware, hardware);
        q.eq(StringUtils.isNotBlank(channel), OtaPackageEntity::getChannel, channel);
        q.eq(StringUtils.isNotBlank(status), OtaPackageEntity::getStatus, status);
        q.orderByDesc(OtaPackageEntity::getCreatedAt);
        return packageDao.selectList(q).stream().map(this::toPackageVo).toList();
    }

    @Override
    @Transactional
    public void deletePackage(String id) {
        OtaPackageEntity pkg = packageDao.selectById(id);
        if (pkg == null) {
            throw new RenException("包不存在");
        }
        if (!"draft".equals(pkg.getStatus())) {
            throw new RenException("仅 draft 状态可删除");
        }
        packageDao.deleteById(id);
    }

    @Override
    public DevicesListVO listDevices(Integer page, Integer pageSize, String keyword, String hardware,
            String deviceType, String channel) {
        int p = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        LambdaQueryWrapper<DeviceEntity> q = new LambdaQueryWrapper<>();
        q.eq(StringUtils.isNotBlank(hardware), DeviceEntity::getBoard, hardware);
        q.eq(StringUtils.isNotBlank(deviceType), DeviceEntity::getDeviceType, deviceType);
        q.eq(StringUtils.isNotBlank(channel), DeviceEntity::getOtaChannel, channel);
        q.orderByDesc(DeviceEntity::getLastConnectedAt);
        List<DeviceEntity> all = deviceDao.selectList(q);
        List<DeviceOtaViewVO> views = new ArrayList<>();
        ManifestIndex index = loadManifestIndex();
        String kw = StringUtils.trimToEmpty(keyword).toLowerCase(Locale.ROOT);
        for (DeviceEntity device : all) {
            DeviceOtaViewVO vo = toDeviceView(device, index);
            if (StringUtils.isNotBlank(kw)) {
                boolean hit = contains(vo.getMacAddress(), kw)
                        || contains(vo.getDeviceId(), kw)
                        || contains(vo.getParentDisplayName(), kw);
                if (!hit) {
                    continue;
                }
            }
            views.add(vo);
        }
        int from = Math.min((p - 1) * size, views.size());
        int to = Math.min(from + size, views.size());
        return new DevicesListVO(views.subList(from, to), views.size());
    }

    @Override
    public List<WhitelistPoolVO> listPools() {
        return poolDao.selectList(new LambdaQueryWrapper<OtaWhitelistPoolEntity>()
                .orderByDesc(OtaWhitelistPoolEntity::getUpdatedAt)).stream().map(this::toPoolVo).toList();
    }

    @Override
    @Transactional
    public WhitelistPoolVO createPool(WhitelistPoolCreateDTO dto) {
        Date now = new Date();
        OtaWhitelistPoolEntity pool = new OtaWhitelistPoolEntity();
        pool.setName(dto.getName().trim());
        pool.setDescription(dto.getDescription());
        pool.setCreatedAt(now);
        pool.setUpdatedAt(now);
        poolDao.insert(pool);
        addMacs(pool.getId(), dto.getMacAddresses());
        return toPoolVo(pool);
    }

    @Override
    @Transactional
    public WhitelistPoolVO updatePool(Long id, WhitelistPoolUpdateDTO dto) {
        OtaWhitelistPoolEntity pool = requirePool(id);
        if (dto.getName() != null) {
            pool.setName(dto.getName().trim());
        }
        if (dto.getDescription() != null) {
            pool.setDescription(dto.getDescription());
        }
        pool.setUpdatedAt(new Date());
        poolDao.updateById(pool);
        return toPoolVo(pool);
    }

    @Override
    @Transactional
    public void deletePool(Long id) {
        requirePool(id);
        poolDeviceDao.delete(new LambdaQueryWrapper<OtaWhitelistPoolDeviceEntity>()
                .eq(OtaWhitelistPoolDeviceEntity::getPoolId, id));
        releasePoolDao.delete(new LambdaQueryWrapper<OtaReleasePoolEntity>()
                .eq(OtaReleasePoolEntity::getPoolId, id));
        poolDao.deleteById(id);
    }

    @Override
    @Transactional
    public WhitelistPoolVO addPoolDevices(Long id, PoolDevicesAddDTO dto) {
        OtaWhitelistPoolEntity pool = requirePool(id);
        addMacs(id, dto.getMacAddresses());
        pool.setUpdatedAt(new Date());
        poolDao.updateById(pool);
        return toPoolVo(pool);
    }

    @Override
    @Transactional
    public void removePoolDevice(Long id, String mac) {
        requirePool(id);
        String macL = OtaRolloutMatcher.normalizeMac(mac);
        poolDeviceDao.delete(new LambdaQueryWrapper<OtaWhitelistPoolDeviceEntity>()
                .eq(OtaWhitelistPoolDeviceEntity::getPoolId, id)
                .eq(OtaWhitelistPoolDeviceEntity::getMacAddress, macL));
    }

    @Override
    public List<ReleaseVO> listReleases() {
        List<OtaReleaseEntity> rows = releaseDao.selectList(new LambdaQueryWrapper<OtaReleaseEntity>()
                .orderByDesc(OtaReleaseEntity::getPublishedAt));
        return rows.stream().map(r -> toReleaseVo(r, true)).toList();
    }

    @Override
    @Transactional
    public ReleaseVO createRelease(ReleaseCreateDTO dto, String publishedBy) {
        OtaPackageEntity pkg = packageDao.selectById(dto.getPackageId());
        if (pkg == null) {
            throw new RenException("包不存在");
        }
        if (!"draft".equals(pkg.getStatus()) && !"published".equals(pkg.getStatus())) {
            throw new RenException("包状态不可发布");
        }
        int rollout = dto.getRolloutPercent() == null ? 100 : dto.getRolloutPercent();
        if (rollout < 1 || rollout > 100) {
            throw new RenException("灰度比例须在 1-100");
        }
        String channel = dto.getChannel().toLowerCase(Locale.ROOT);
        List<OtaReleaseEntity> actives = findActiveReleases(pkg.getHardware(), channel, pkg.getType());
        OtaReleaseEntity prev = actives.stream()
                .max(Comparator.comparing(OtaReleaseEntity::getPublishedAt, Comparator.nullsLast(Date::compareTo)))
                .orElse(null);
        for (OtaReleaseEntity rel : actives) {
            rel.setStatus(OtaReleaseStateMachine.SUPERSEDED);
            releaseDao.updateById(rel);
        }
        pkg.setStatus("published");
        pkg.setChannel(channel);
        packageDao.updateById(pkg);

        Date now = new Date();
        OtaReleaseEntity release = new OtaReleaseEntity();
        release.setPackageId(pkg.getId());
        release.setChannel(channel);
        release.setRolloutPercent(rollout);
        release.setStatus(OtaReleaseStateMachine.ACTIVE);
        release.setPreviousReleaseId(prev == null ? null : prev.getId());
        release.setExtraMacAddresses(writeMacsJson(dto.getExtraMacAddresses()));
        release.setPublishedBy(StringUtils.defaultIfBlank(publishedBy, "devops"));
        release.setPublishedAt(now);
        releaseDao.insert(release);

        Set<Long> poolIds = dto.getWhitelistPoolIds() == null ? Set.of()
                : dto.getWhitelistPoolIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        for (Long poolId : poolIds) {
            if (poolDao.selectById(poolId) != null) {
                OtaReleasePoolEntity link = new OtaReleasePoolEntity();
                link.setReleaseId(release.getId());
                link.setPoolId(poolId);
                releasePoolDao.insert(link);
            }
        }

        Set<String> whitelist = collectReleaseWhitelist(release);
        List<DeviceEntity> devices = deviceDao.selectList(new LambdaQueryWrapper<DeviceEntity>()
                .eq(DeviceEntity::getBoard, pkg.getHardware()));
        for (DeviceEntity device : devices) {
            if (!deviceEligible(device, release, pkg, whitelist)) {
                continue;
            }
            OtaDeviceUpgradeLogEntity logRow = new OtaDeviceUpgradeLogEntity();
            logRow.setReleaseId(release.getId());
            logRow.setMacAddress(OtaRolloutMatcher.normalizeMac(device.getMacAddress()));
            logRow.setPkgType(pkg.getType());
            logRow.setFromVersion("system".equals(pkg.getType()) ? device.getSystemVersion() : device.getAppVersion());
            logRow.setToVersion(pkg.getVersion());
            logRow.setStatus("pending");
            logRow.setReportedAt(now);
            upgradeLogDao.insert(logRow);
        }
        return toReleaseVo(release, true);
    }

    @Override
    @Transactional
    public ReleaseVO rollbackRelease(Long id, ReleaseRollbackDTO dto) {
        OtaReleaseEntity release = releaseDao.selectById(id);
        if (release == null) {
            throw new RenException("发布不存在");
        }
        ReleaseCoverageVO cov = computeCoverage(release, false);
        Long explicitTarget = resolveExplicitTarget(dto, release);
        OtaReleaseStateMachine.RollbackPlan plan;
        try {
            plan = OtaReleaseStateMachine.planRollback(
                    release.getChannel(), release.getStatus(),
                    cov.getSuccessCount() == null ? 0 : cov.getSuccessCount(),
                    release.getPreviousReleaseId(), explicitTarget);
        } catch (IllegalStateException e) {
            throw new RenException(e.getMessage());
        }
        release.setStatus(plan.newStatus());
        releaseDao.updateById(release);
        if (plan.shouldReactivatePrevious()) {
            OtaReleaseEntity target = releaseDao.selectById(plan.reactivateReleaseId());
            if (target != null) {
                target.setStatus(OtaReleaseStateMachine.ACTIVE);
                releaseDao.updateById(target);
            }
        }
        return toReleaseVo(release, true);
    }

    @Override
    public ReleaseCoverageDetailVO coverage(Long id) {
        OtaReleaseEntity release = releaseDao.selectById(id);
        if (release == null) {
            throw new RenException("发布不存在");
        }
        return computeCoverage(release, true);
    }

    @Override
    @Transactional
    public DeviceOtaCheckRespVO checkManifest(DeviceOtaCheckReqDTO req) {
        DeviceEntity device = findOrTouchDevice(req);
        reconcileAlreadyOnTarget(device);
        return buildManifest(device, true);
    }

    @Override
    @Transactional
    public DeviceOtaCheckRespVO checkManifestForDevice(DeviceEntity device) {
        if (device == null) {
            return new DeviceOtaCheckRespVO();
        }
        reconcileAlreadyOnTarget(device);
        return buildManifest(device, true);
    }

    @Override
    @Transactional
    public void reportUpgrade(DeviceOtaReportDTO dto) {
        OtaReleaseEntity release = releaseDao.selectById(dto.getReleaseId());
        if (release == null) {
            throw new RenException("发布不存在");
        }
        String mac = OtaRolloutMatcher.normalizeMac(dto.getMacAddress());
        OtaDeviceUpgradeLogEntity latest = latestLog(release.getId(), mac);
        Date now = new Date();
        if (latest == null) {
            latest = new OtaDeviceUpgradeLogEntity();
            latest.setReleaseId(release.getId());
            latest.setMacAddress(mac);
            latest.setPkgType(dto.getType());
            latest.setFromVersion(dto.getFromVersion());
            latest.setToVersion(dto.getToVersion());
            latest.setStatus(dto.getStatus());
            latest.setErrorMessage(dto.getErrorMessage());
            latest.setReportedAt(now);
            upgradeLogDao.insert(latest);
        } else {
            latest.setPkgType(dto.getType());
            if (StringUtils.isNotBlank(dto.getFromVersion())) {
                latest.setFromVersion(dto.getFromVersion());
            }
            if (StringUtils.isNotBlank(dto.getToVersion())) {
                latest.setToVersion(dto.getToVersion());
            }
            latest.setStatus(dto.getStatus());
            latest.setErrorMessage(dto.getErrorMessage());
            latest.setReportedAt(now);
            upgradeLogDao.updateById(latest);
        }
        if ("success".equals(dto.getStatus())) {
            DeviceEntity device = findDeviceByMac(mac);
            if (device != null) {
                String ver = StringUtils.defaultIfBlank(dto.getToVersion(), latest.getToVersion());
                if ("system".equals(dto.getType()) && StringUtils.isNotBlank(ver)) {
                    device.setSystemVersion(ver);
                }
                if ("app".equals(dto.getType()) && StringUtils.isNotBlank(ver)) {
                    device.setAppVersion(ver);
                }
                deviceDao.updateById(device);
            }
        }
    }

    @Override
    public boolean isKnownHardware(String hardware) {
        if (StringUtils.isBlank(hardware)) {
            return false;
        }
        OtaHardwareTypeEntity hw = hardwareTypeDao.selectById(hardware.trim());
        return hw != null && hw.getEnabled() != null && hw.getEnabled() == 1;
    }

    /**
     * 设备已在目标版本但漏报 success 时补记，避免覆盖度偏低。
     */
    private void reconcileAlreadyOnTarget(DeviceEntity device) {
        if (device == null
                || (StringUtils.isBlank(device.getId()) && StringUtils.isBlank(device.getMacAddress()))) {
            return;
        }
        ManifestIndex index = loadManifestIndex();
        Date now = new Date();
        for (String type : List.of("system", "app")) {
            VisibleRelease visible = findVisibleIncludingCurrent(device, type, index);
            if (visible == null || visible.pkg == null) {
                continue;
            }
            String current = "system".equals(type) ? device.getSystemVersion() : device.getAppVersion();
            if (StringUtils.isBlank(current) || OtaVersionUtils.compare(current, visible.pkg.getVersion()) != 0) {
                continue;
            }
            String mac = OtaRolloutMatcher.normalizeMac(device.getMacAddress());
            OtaDeviceUpgradeLogEntity latest = latestLog(visible.release.getId(), mac);
            if (latest != null && "success".equals(latest.getStatus())) {
                continue;
            }
            if (latest == null) {
                OtaDeviceUpgradeLogEntity row = new OtaDeviceUpgradeLogEntity();
                row.setReleaseId(visible.release.getId());
                row.setMacAddress(mac);
                row.setPkgType(type);
                row.setToVersion(visible.pkg.getVersion());
                row.setStatus("success");
                row.setReportedAt(now);
                upgradeLogDao.insert(row);
            } else {
                latest.setStatus("success");
                latest.setToVersion(visible.pkg.getVersion());
                latest.setReportedAt(now);
                upgradeLogDao.updateById(latest);
            }
        }
    }

    private VisibleRelease findVisibleIncludingCurrent(DeviceEntity device, String pkgType, ManifestIndex index) {
        VisibleRelease best = null;
        for (String ch : OtaRolloutMatcher.visibleChannels(device.getOtaChannel())) {
            List<IndexedRelease> candidates = index.actives.getOrDefault(indexKey(device.getBoard(), ch, pkgType),
                    List.of());
            for (IndexedRelease ir : candidates) {
                if (!deviceEligible(device, ir.release, ir.pkg, ir.whitelistMacs)) {
                    continue;
                }
                if (best == null || OtaVersionUtils.compare(ir.pkg.getVersion(), best.pkg.getVersion()) > 0) {
                    best = new VisibleRelease(ir.release, ir.pkg, false);
                }
            }
        }
        return best;
    }

    private DeviceOtaCheckRespVO buildManifest(DeviceEntity device, boolean newerOnly) {
        DeviceOtaCheckRespVO resp = new DeviceOtaCheckRespVO();
        if (device == null || StringUtils.isBlank(device.getBoard())) {
            return resp;
        }
        if (device.getAutoUpdate() != null && device.getAutoUpdate() == 0) {
            return resp;
        }
        ManifestIndex index = loadManifestIndex();
        for (String type : List.of("system", "app")) {
            VisibleRelease visible = findVisible(device, type, index);
            if (visible == null) {
                continue;
            }
            String current = "system".equals(type)
                    ? StringUtils.defaultIfBlank(device.getSystemVersion(), "0.0.0")
                    : StringUtils.defaultIfBlank(device.getAppVersion(), "0.0.0");
            boolean newer = OtaVersionUtils.isNewer(visible.pkg.getVersion(), current);
            boolean downgrade = !newer && visible.downgrade
                    && OtaVersionUtils.compare(visible.pkg.getVersion(), current) < 0;
            if (newerOnly && !newer && !downgrade) {
                continue;
            }
            if (!newer && !downgrade) {
                continue;
            }
            DeviceOtaCheckRespVO.UpdateItem item = new DeviceOtaCheckRespVO.UpdateItem();
            item.setVersion(visible.pkg.getVersion());
            item.setUrl(storageService.resolveAccessUrl(visible.pkg.getOssKey()));
            item.setSha256(visible.pkg.getSha256());
            item.setReleaseId(visible.release.getId());
            item.setMandatory(false);
            resp.getUpdates().put(type, item);
        }
        return resp;
    }

    private VisibleRelease findVisible(DeviceEntity device, String pkgType, ManifestIndex index) {
        VisibleRelease best = null;
        for (String ch : OtaRolloutMatcher.visibleChannels(device.getOtaChannel())) {
            List<IndexedRelease> candidates = index.actives.getOrDefault(indexKey(device.getBoard(), ch, pkgType),
                    List.of());
            for (IndexedRelease ir : candidates) {
                if (!deviceEligible(device, ir.release, ir.pkg, ir.whitelistMacs)) {
                    continue;
                }
                if (best == null || OtaVersionUtils.compare(ir.pkg.getVersion(), best.pkg.getVersion()) > 0
                        || (OtaVersionUtils.compare(ir.pkg.getVersion(), best.pkg.getVersion()) == 0
                                && "beta".equals(ir.release.getChannel()))) {
                    best = new VisibleRelease(ir.release, ir.pkg, false);
                }
            }
        }
        VisibleRelease downgrade = findDowngrade(device, pkgType, index);
        if (best == null) {
            return downgrade;
        }
        return best;
    }

    private VisibleRelease findDowngrade(DeviceEntity device, String pkgType, ManifestIndex index) {
        String current = "system".equals(pkgType) ? device.getSystemVersion() : device.getAppVersion();
        if (StringUtils.isBlank(current)) {
            return null;
        }
        for (OtaReleaseEntity rolled : index.rolledBack) {
            OtaPackageEntity pkg = index.packages.get(rolled.getPackageId());
            if (pkg == null || !pkgType.equals(pkg.getType()) || !Objects.equals(device.getBoard(), pkg.getHardware())) {
                continue;
            }
            if (!current.equals(pkg.getVersion()) || rolled.getPreviousReleaseId() == null) {
                continue;
            }
            OtaReleaseEntity prev = index.byId.get(rolled.getPreviousReleaseId());
            if (prev == null || !OtaReleaseStateMachine.ACTIVE.equals(prev.getStatus())) {
                continue;
            }
            OtaPackageEntity prevPkg = index.packages.get(prev.getPackageId());
            if (prevPkg == null) {
                continue;
            }
            return new VisibleRelease(prev, prevPkg, true);
        }
        return null;
    }

    private boolean deviceEligible(DeviceEntity device, OtaReleaseEntity release, OtaPackageEntity pkg,
            Collection<String> whitelistMacs) {
        if (device == null || release == null || pkg == null) {
            return false;
        }
        if (!OtaReleaseStateMachine.ACTIVE.equals(release.getStatus())) {
            return false;
        }
        if (!Objects.equals(device.getBoard(), pkg.getHardware())) {
            return false;
        }
        if (!OtaRolloutMatcher.channelCanSeeRelease(device.getOtaChannel(), release.getChannel())) {
            return false;
        }
        return OtaRolloutMatcher.isEligible(device.getMacAddress(),
                release.getRolloutPercent() == null ? 0 : release.getRolloutPercent(), whitelistMacs);
    }

    private ManifestIndex loadManifestIndex() {
        ManifestIndex index = new ManifestIndex();
        List<OtaPackageEntity> pkgs = packageDao.selectList(new LambdaQueryWrapper<>());
        for (OtaPackageEntity pkg : pkgs) {
            index.packages.put(pkg.getId(), pkg);
        }
        List<OtaReleaseEntity> releases = releaseDao.selectList(new LambdaQueryWrapper<>());
        Map<Long, Set<String>> whitelistByRelease = loadWhitelistByRelease(
                releases.stream().map(OtaReleaseEntity::getId).toList());
        for (OtaReleaseEntity rel : releases) {
            index.byId.put(rel.getId(), rel);
            OtaPackageEntity pkg = index.packages.get(rel.getPackageId());
            if (pkg == null) {
                continue;
            }
            Set<String> macs = new HashSet<>(whitelistByRelease.getOrDefault(rel.getId(), Set.of()));
            macs.addAll(parseMacsJson(rel.getExtraMacAddresses()));
            IndexedRelease ir = new IndexedRelease(rel, pkg, macs);
            if (OtaReleaseStateMachine.ACTIVE.equals(rel.getStatus())) {
                index.actives.computeIfAbsent(indexKey(pkg.getHardware(), rel.getChannel(), pkg.getType()),
                        k -> new ArrayList<>()).add(ir);
            }
            if (OtaReleaseStateMachine.ROLLED_BACK.equals(rel.getStatus())) {
                index.rolledBack.add(rel);
            }
        }
        return index;
    }

    private Map<Long, Set<String>> loadWhitelistByRelease(List<Long> releaseIds) {
        Map<Long, Set<String>> out = new HashMap<>();
        if (releaseIds == null || releaseIds.isEmpty()) {
            return out;
        }
        List<OtaReleasePoolEntity> links = releasePoolDao.selectList(new LambdaQueryWrapper<OtaReleasePoolEntity>()
                .in(OtaReleasePoolEntity::getReleaseId, releaseIds));
        if (links.isEmpty()) {
            return out;
        }
        Set<Long> poolIds = links.stream().map(OtaReleasePoolEntity::getPoolId).collect(Collectors.toSet());
        List<OtaWhitelistPoolDeviceEntity> devices = poolDeviceDao.selectList(
                new LambdaQueryWrapper<OtaWhitelistPoolDeviceEntity>()
                        .in(OtaWhitelistPoolDeviceEntity::getPoolId, poolIds));
        Map<Long, Set<String>> poolMacs = new HashMap<>();
        for (OtaWhitelistPoolDeviceEntity d : devices) {
            poolMacs.computeIfAbsent(d.getPoolId(), k -> new HashSet<>())
                    .add(OtaRolloutMatcher.normalizeMac(d.getMacAddress()));
        }
        for (OtaReleasePoolEntity link : links) {
            out.computeIfAbsent(link.getReleaseId(), k -> new HashSet<>())
                    .addAll(poolMacs.getOrDefault(link.getPoolId(), Set.of()));
        }
        return out;
    }

    private Set<String> collectReleaseWhitelist(OtaReleaseEntity release) {
        Set<String> macs = new HashSet<>(parseMacsJson(release.getExtraMacAddresses()));
        List<OtaReleasePoolEntity> links = releasePoolDao.selectList(new LambdaQueryWrapper<OtaReleasePoolEntity>()
                .eq(OtaReleasePoolEntity::getReleaseId, release.getId()));
        if (links.isEmpty()) {
            return macs;
        }
        List<Long> poolIds = links.stream().map(OtaReleasePoolEntity::getPoolId).toList();
        List<OtaWhitelistPoolDeviceEntity> devices = poolDeviceDao.selectList(
                new LambdaQueryWrapper<OtaWhitelistPoolDeviceEntity>()
                        .in(OtaWhitelistPoolDeviceEntity::getPoolId, poolIds));
        for (OtaWhitelistPoolDeviceEntity d : devices) {
            macs.add(OtaRolloutMatcher.normalizeMac(d.getMacAddress()));
        }
        return macs;
    }

    private List<OtaReleaseEntity> findActiveReleases(String hardware, String channel, String type) {
        List<OtaReleaseEntity> actives = releaseDao.selectList(new LambdaQueryWrapper<OtaReleaseEntity>()
                .eq(OtaReleaseEntity::getStatus, OtaReleaseStateMachine.ACTIVE)
                .eq(OtaReleaseEntity::getChannel, channel));
        List<OtaReleaseEntity> matched = new ArrayList<>();
        for (OtaReleaseEntity rel : actives) {
            OtaPackageEntity pkg = packageDao.selectById(rel.getPackageId());
            if (pkg != null && hardware.equals(pkg.getHardware()) && type.equals(pkg.getType())) {
                matched.add(rel);
            }
        }
        return matched;
    }

    private ReleaseCoverageDetailVO computeCoverage(OtaReleaseEntity release, boolean withDevices) {
        OtaPackageEntity pkg = packageDao.selectById(release.getPackageId());
        Set<String> whitelist = collectReleaseWhitelist(release);
        List<DeviceEntity> hardwareDevices = pkg == null ? List.of()
                : deviceDao.selectList(new LambdaQueryWrapper<DeviceEntity>()
                        .eq(DeviceEntity::getBoard, pkg.getHardware()));
        List<DeviceEntity> eligible = new ArrayList<>();
        for (DeviceEntity device : hardwareDevices) {
            if (deviceEligible(device, release, pkg, whitelist)) {
                eligible.add(device);
            }
        }
        List<OtaDeviceUpgradeLogEntity> logs = upgradeLogDao.selectList(
                new LambdaQueryWrapper<OtaDeviceUpgradeLogEntity>()
                        .eq(OtaDeviceUpgradeLogEntity::getReleaseId, release.getId())
                        .orderByDesc(OtaDeviceUpgradeLogEntity::getReportedAt));
        Map<String, OtaDeviceUpgradeLogEntity> latestByMac = new LinkedHashMap<>();
        for (OtaDeviceUpgradeLogEntity logRow : logs) {
            latestByMac.putIfAbsent(OtaRolloutMatcher.normalizeMac(logRow.getMacAddress()), logRow);
        }
        Set<String> eligibleMacs = eligible.stream()
                .map(d -> OtaRolloutMatcher.normalizeMac(d.getMacAddress()))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toSet());
        int success = 0;
        int failed = 0;
        int downloading = 0;
        int pending = 0;
        for (String mac : eligibleMacs) {
            OtaDeviceUpgradeLogEntity logRow = latestByMac.get(mac);
            String status = logRow == null ? "pending" : logRow.getStatus();
            switch (StringUtils.defaultIfBlank(status, "pending")) {
                case "success" -> success++;
                case "failed" -> failed++;
                case "downloading" -> downloading++;
                default -> pending++;
            }
        }
        int eligibleCount = eligibleMacs.size();
        ReleaseCoverageDetailVO vo = new ReleaseCoverageDetailVO();
        vo.setEligibleCount(eligibleCount);
        vo.setSuccessCount(success);
        vo.setFailedCount(failed);
        vo.setDownloadingCount(downloading);
        vo.setPendingCount(pending);
        vo.setPercent(eligibleCount == 0 ? 0.0 : Math.round(success * 1000.0 / eligibleCount) / 10.0);
        if (withDevices) {
            boolean rolledBack = OtaReleaseStateMachine.ROLLED_BACK.equals(release.getStatus());
            for (DeviceEntity device : eligible) {
                String mac = OtaRolloutMatcher.normalizeMac(device.getMacAddress());
                OtaDeviceUpgradeLogEntity logRow = latestByMac.get(mac);
                CoverageDeviceVO item = new CoverageDeviceVO();
                item.setMacAddress(device.getMacAddress());
                item.setStatus(logRow == null ? "pending" : logRow.getStatus());
                item.setFromVersion(logRow == null ? null : logRow.getFromVersion());
                item.setToVersion(logRow == null ? null : logRow.getToVersion());
                item.setReportedAt(logRow == null ? null : logRow.getReportedAt());
                item.setNeedsManualHandle(rolledBack && logRow != null && "success".equals(logRow.getStatus()));
                vo.getDevices().add(item);
            }
        }
        return vo;
    }

    private Long resolveExplicitTarget(ReleaseRollbackDTO dto, OtaReleaseEntity current) {
        if (dto == null || StringUtils.isBlank(dto.getPackageId())) {
            return null;
        }
        OtaPackageEntity pkg = packageDao.selectById(dto.getPackageId());
        if (pkg == null) {
            throw new RenException("回滚目标包不存在");
        }
        List<OtaReleaseEntity> history = releaseDao.selectList(new LambdaQueryWrapper<OtaReleaseEntity>()
                .eq(OtaReleaseEntity::getPackageId, pkg.getId())
                .ne(OtaReleaseEntity::getId, current.getId())
                .orderByDesc(OtaReleaseEntity::getPublishedAt));
        if (!history.isEmpty()) {
            return history.get(0).getId();
        }
        OtaReleaseEntity created = new OtaReleaseEntity();
        created.setPackageId(pkg.getId());
        created.setChannel(current.getChannel());
        created.setRolloutPercent(current.getRolloutPercent());
        created.setStatus(OtaReleaseStateMachine.SUPERSEDED);
        created.setPublishedBy("rollback");
        created.setPublishedAt(new Date());
        releaseDao.insert(created);
        pkg.setStatus("published");
        packageDao.updateById(pkg);
        return created.getId();
    }

    private DeviceEntity findOrTouchDevice(DeviceOtaCheckReqDTO req) {
        DeviceEntity device = findDeviceByMac(req.getMacAddress());
        if (device == null) {
            device = new DeviceEntity();
            device.setMacAddress(OtaRolloutMatcher.normalizeMac(req.getMacAddress()));
            device.setBoard(req.getBoard());
            device.setDeviceType(req.getDeviceType());
            device.setSystemVersion(req.getSystemVersion());
            device.setAppVersion(req.getAppVersion());
            device.setOtaChannel(StringUtils.defaultIfBlank(req.getOtaChannel(), "stable"));
            return device;
        }
        boolean dirty = false;
        if (StringUtils.isNotBlank(req.getBoard()) && !req.getBoard().equals(device.getBoard())) {
            device.setBoard(req.getBoard());
            dirty = true;
        }
        if (StringUtils.isNotBlank(req.getDeviceType())) {
            device.setDeviceType(req.getDeviceType());
            dirty = true;
        }
        if (StringUtils.isNotBlank(req.getSystemVersion())) {
            device.setSystemVersion(req.getSystemVersion());
            dirty = true;
        }
        if (StringUtils.isNotBlank(req.getAppVersion())) {
            device.setAppVersion(req.getAppVersion());
            dirty = true;
        }
        if (StringUtils.isNotBlank(req.getOtaChannel())) {
            device.setOtaChannel(req.getOtaChannel());
            dirty = true;
        }
        device.setLastConnectedAt(new Date());
        dirty = true;
        if (dirty) {
            deviceDao.updateById(device);
        }
        return device;
    }

    private DeviceEntity findDeviceByMac(String mac) {
        if (StringUtils.isBlank(mac)) {
            return null;
        }
        DeviceEntity device = deviceDao.selectOne(new LambdaQueryWrapper<DeviceEntity>()
                .eq(DeviceEntity::getMacAddress, mac));
        if (device != null) {
            return device;
        }
        return deviceDao.selectByIdOrMacVariant(mac);
    }

    private OtaDeviceUpgradeLogEntity latestLog(Long releaseId, String mac) {
        return upgradeLogDao.selectOne(new LambdaQueryWrapper<OtaDeviceUpgradeLogEntity>()
                .eq(OtaDeviceUpgradeLogEntity::getReleaseId, releaseId)
                .eq(OtaDeviceUpgradeLogEntity::getMacAddress, mac)
                .orderByDesc(OtaDeviceUpgradeLogEntity::getReportedAt)
                .last("LIMIT 1"));
    }

    private void addMacs(Long poolId, List<String> macs) {
        if (macs == null) {
            return;
        }
        Set<String> existing = poolDeviceDao.selectList(new LambdaQueryWrapper<OtaWhitelistPoolDeviceEntity>()
                .eq(OtaWhitelistPoolDeviceEntity::getPoolId, poolId)).stream()
                .map(d -> OtaRolloutMatcher.normalizeMac(d.getMacAddress())).collect(Collectors.toSet());
        for (String raw : macs) {
            String mac = OtaRolloutMatcher.normalizeMac(raw);
            if (mac.isEmpty() || existing.contains(mac)) {
                continue;
            }
            OtaWhitelistPoolDeviceEntity row = new OtaWhitelistPoolDeviceEntity();
            row.setPoolId(poolId);
            row.setMacAddress(mac);
            poolDeviceDao.insert(row);
            existing.add(mac);
        }
    }

    private OtaHardwareTypeEntity requireHardware(String key) {
        OtaHardwareTypeEntity row = hardwareTypeDao.selectById(key);
        if (row == null) {
            throw new RenException("硬件类型不存在");
        }
        return row;
    }

    private OtaWhitelistPoolEntity requirePool(Long id) {
        OtaWhitelistPoolEntity pool = poolDao.selectById(id);
        if (pool == null) {
            throw new RenException("白名单池不存在");
        }
        return pool;
    }

    private HardwareTypeVO toHardwareVo(OtaHardwareTypeEntity row) {
        HardwareTypeVO vo = new HardwareTypeVO();
        vo.setKey(row.getHwKey());
        vo.setName(row.getName());
        vo.setDescription(row.getDescription());
        vo.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
        vo.setCreatedAt(row.getCreatedAt());
        vo.setUpdatedAt(row.getUpdatedAt());
        return vo;
    }

    private PackageVO toPackageVo(OtaPackageEntity pkg) {
        PackageVO vo = new PackageVO();
        vo.setId(pkg.getId());
        vo.setType(pkg.getType());
        vo.setHardware(pkg.getHardware());
        vo.setVersion(pkg.getVersion());
        vo.setChannel(pkg.getChannel());
        vo.setFilename(pkg.getFilename());
        vo.setOssKey(pkg.getOssKey());
        vo.setSizeBytes(pkg.getSizeBytes());
        vo.setSha256(pkg.getSha256());
        vo.setStatus(pkg.getStatus());
        vo.setNotes(pkg.getNotes());
        vo.setCreatedBy(pkg.getCreatedBy());
        vo.setCreatedAt(pkg.getCreatedAt());
        return vo;
    }

    private WhitelistPoolVO toPoolVo(OtaWhitelistPoolEntity pool) {
        Long count = poolDeviceDao.selectCount(new LambdaQueryWrapper<OtaWhitelistPoolDeviceEntity>()
                .eq(OtaWhitelistPoolDeviceEntity::getPoolId, pool.getId()));
        WhitelistPoolVO vo = new WhitelistPoolVO();
        vo.setId(pool.getId());
        vo.setName(pool.getName());
        vo.setDescription(pool.getDescription());
        vo.setDeviceCount(count == null ? 0 : count.intValue());
        vo.setCreatedAt(pool.getCreatedAt());
        vo.setUpdatedAt(pool.getUpdatedAt());
        return vo;
    }

    private ReleaseVO toReleaseVo(OtaReleaseEntity release, boolean withCoverage) {
        OtaPackageEntity pkg = packageDao.selectById(release.getPackageId());
        List<OtaReleasePoolEntity> links = releasePoolDao.selectList(new LambdaQueryWrapper<OtaReleasePoolEntity>()
                .eq(OtaReleasePoolEntity::getReleaseId, release.getId()));
        ReleaseVO vo = new ReleaseVO();
        vo.setId(release.getId());
        vo.setPackageId(release.getPackageId());
        vo.setType(pkg == null ? "" : pkg.getType());
        vo.setHardware(pkg == null ? "" : pkg.getHardware());
        vo.setVersion(pkg == null ? "" : pkg.getVersion());
        vo.setChannel(release.getChannel());
        vo.setRolloutPercent(release.getRolloutPercent());
        vo.setWhitelistPoolIds(links.stream().map(OtaReleasePoolEntity::getPoolId).toList());
        vo.setStatus(release.getStatus());
        vo.setPublishedAt(release.getPublishedAt());
        vo.setPublishedBy(release.getPublishedBy());
        vo.setPreviousReleaseId(release.getPreviousReleaseId());
        vo.setRollbackAvailable("beta".equals(release.getChannel())
                && OtaReleaseStateMachine.ACTIVE.equals(release.getStatus()));
        if (withCoverage) {
            vo.setCoverage(computeCoverage(release, false));
        }
        return vo;
    }

    private DeviceOtaViewVO toDeviceView(DeviceEntity device, ManifestIndex index) {
        DeviceOtaViewVO vo = new DeviceOtaViewVO();
        vo.setDeviceId(StringUtils.defaultIfBlank(device.getId(), device.getMacAddress()));
        vo.setMacAddress(StringUtils.defaultString(device.getMacAddress()));
        vo.setBoard(StringUtils.defaultString(device.getBoard()));
        vo.setDeviceType(StringUtils.defaultString(device.getDeviceType()));
        String systemVersion = device.getSystemVersion();
        String appVersion = device.getAppVersion();
        // 未迁移时 app_version 仍是固件，归到 System；App 仅在已有 system_version 时才展示
        if (StringUtils.isBlank(systemVersion) && StringUtils.isNotBlank(appVersion)) {
            systemVersion = appVersion;
            appVersion = "";
        }
        vo.setSystemVersion(StringUtils.defaultIfBlank(systemVersion, "0.0.0"));
        vo.setAppVersion(StringUtils.defaultString(appVersion));
        vo.setOtaChannel(StringUtils.defaultIfBlank(device.getOtaChannel(), "stable"));
        vo.setAutoUpdate(device.getAutoUpdate() == null || device.getAutoUpdate() != 0);
        vo.setLastConnectedAt(device.getLastConnectedAt());
        vo.setOnline(isOnline(device.getLastConnectedAt()));
        vo.setParentDisplayName(resolveDisplayName(device));
        VisibleRelease sys = findVisible(device, "system", index);
        VisibleRelease app = findVisible(device, "app", index);
        Map<String, String> latest = new LinkedHashMap<>();
        latest.put("system", sys == null ? null : sys.pkg.getVersion());
        latest.put("app", app == null ? null : app.pkg.getVersion());
        vo.setLatestVisible(latest);
        Map<String, Boolean> available = new LinkedHashMap<>();
        available.put("system", sys != null && OtaVersionUtils.isNewer(sys.pkg.getVersion(), vo.getSystemVersion()));
        available.put("app", app != null && OtaVersionUtils.isNewer(app.pkg.getVersion(), vo.getAppVersion()));
        vo.setUpdateAvailable(available);
        return vo;
    }

    private String resolveDisplayName(DeviceEntity device) {
        if (StringUtils.isNotBlank(device.getAlias())) {
            return device.getAlias();
        }
        if (StringUtils.isNotBlank(device.getId())) {
            return ParentDeviceDisplayResolver.resolveDeviceName(
                    deviceDao, deviceChildDao, parentDeviceBindingDao, device.getId());
        }
        return device.getMacAddress();
    }

    private static boolean isOnline(Date lastConnectedAt) {
        return lastConnectedAt != null && System.currentTimeMillis() - lastConnectedAt.getTime() <= ONLINE_WINDOW_MS;
    }

    private static boolean contains(String value, String kw) {
        return StringUtils.isNotBlank(value) && value.toLowerCase(Locale.ROOT).contains(kw);
    }

    private static String indexKey(String hardware, String channel, String type) {
        return hardware + "|" + channel + "|" + type;
    }

    private List<String> parseMacsJson(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, STRING_LIST);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeMacsJson(List<String> macs) {
        try {
            return objectMapper.writeValueAsString(macs == null ? List.of() : macs);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RenException("无法计算 sha256", e);
        }
    }

    private static final class ManifestIndex {
        private final Map<String, OtaPackageEntity> packages = new HashMap<>();
        private final Map<Long, OtaReleaseEntity> byId = new HashMap<>();
        private final Map<String, List<IndexedRelease>> actives = new HashMap<>();
        private final List<OtaReleaseEntity> rolledBack = new ArrayList<>();
    }

    private record IndexedRelease(OtaReleaseEntity release, OtaPackageEntity pkg, Set<String> whitelistMacs) {
    }

    private record VisibleRelease(OtaReleaseEntity release, OtaPackageEntity pkg, boolean downgrade) {
    }
}
