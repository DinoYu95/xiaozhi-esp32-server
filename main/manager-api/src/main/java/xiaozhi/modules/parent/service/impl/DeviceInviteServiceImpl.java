package xiaozhi.modules.parent.service.impl;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.redis.RedisKeys;
import xiaozhi.common.redis.RedisUtils;
import xiaozhi.common.utils.HashEncryptionUtil;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.DeviceInviteDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.dto.DeviceInviteAcceptDTO;
import xiaozhi.modules.parent.dto.DeviceInviteCreateDTO;
import xiaozhi.modules.parent.dto.DeviceInviteRevokeDTO;
import xiaozhi.modules.parent.dto.DeviceMemberLeaveDTO;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.DeviceInviteEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.service.DeviceInviteService;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.util.ParentDeviceAccessHelper;
import xiaozhi.modules.parent.util.ParentDeviceDisplayResolver;
import xiaozhi.modules.parent.util.ParentUserProfileHelper;
import xiaozhi.modules.parent.vo.DeviceInviteAcceptVO;
import xiaozhi.modules.parent.vo.DeviceInviteCreateVO;
import xiaozhi.modules.parent.vo.DeviceInviteItemVO;
import xiaozhi.modules.parent.vo.DeviceInvitePreviewVO;
import xiaozhi.modules.parent.vo.DeviceMemberItemVO;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceInviteServiceImpl implements DeviceInviteService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DeviceInviteDao deviceInviteDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final ParentUserDao parentUserDao;
    private final DeviceDao deviceDao;
    private final DeviceChildDao deviceChildDao;
    private final RedisUtils redisUtils;
    private final ParentStorageService parentStorageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceInviteCreateVO createInvite(Long parentUserId, DeviceInviteCreateDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getDeviceId())) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        String deviceId = dto.getDeviceId().trim();
        ParentDeviceBindingEntity ownerBinding =
                ParentDeviceAccessHelper.requireOwner(parentDeviceBindingDao, parentUserId, deviceId);
        deviceId = ParentDeviceDisplayResolver.canonicalDeviceId(deviceDao, ownerBinding.getDeviceId());
        if (StringUtils.isBlank(deviceId)) {
            deviceId = ownerBinding.getDeviceId();
        }
        if (!deviceId.equals(ownerBinding.getDeviceId())) {
            ownerBinding.setDeviceId(deviceId);
            ownerBinding.setUpdatedAt(new Date());
            parentDeviceBindingDao.updateById(ownerBinding);
        }

        checkInviteRateLimit(deviceId);

        String inviteToken = generateInviteToken();
        String tokenHash = hashToken(inviteToken);
        Date expiresAt = addDays(new Date(), INVITE_EXPIRE_DAYS);

        DeviceInviteEntity invite = new DeviceInviteEntity();
        invite.setDeviceId(deviceId);
        invite.setInviterParentId(parentUserId);
        invite.setTokenHash(tokenHash);
        invite.setExpiresAt(expiresAt);
        invite.setMaxUses(INVITE_MAX_USES);
        invite.setUsedCount(0);
        invite.setStatus(DeviceInviteEntity.STATUS_ACTIVE);
        invite.setCreatedAt(new Date());
        deviceInviteDao.insert(invite);

        log.info("device invite created: deviceId={}, inviter={}, inviteId={}",
                deviceId, parentUserId, invite.getId());

        String deviceName = ParentDeviceDisplayResolver.resolveDeviceName(
                deviceDao, deviceChildDao, parentDeviceBindingDao, deviceId);
        DeviceInviteCreateVO vo = new DeviceInviteCreateVO();
        vo.setInviteToken(inviteToken);
        vo.setExpiresAt(expiresAt);
        vo.setShareTitle("邀请你一起管理「" + deviceName + "」");
        vo.setSharePath("/pages/device-invite/accept?token=" + inviteToken);
        vo.setMaxUses(INVITE_MAX_USES);
        return vo;
    }

    @Override
    public DeviceInvitePreviewVO preview(Long parentUserId, String inviteToken) {
        DeviceInvitePreviewVO vo = new DeviceInvitePreviewVO();
        DeviceInviteEntity invite = findInviteByToken(inviteToken);
        if (invite == null) {
            vo.setValid(false);
            vo.setReason("not_found");
            return vo;
        }
        String invalidReason = resolveInvalidReason(invite);
        vo.setDeviceId(ParentDeviceDisplayResolver.canonicalDeviceId(deviceDao, invite.getDeviceId()));
        vo.setExpiresAt(invite.getExpiresAt());
        vo.setDeviceName(ParentDeviceDisplayResolver.resolveDeviceName(
                deviceDao, deviceChildDao, parentDeviceBindingDao, invite.getDeviceId()));
        ParentUserEntity inviter = parentUserDao.selectById(invite.getInviterParentId());
        vo.setInviterNickname(ParentUserProfileHelper.resolveNicknameOrFallback(inviter));
        boolean alreadyMember = ParentDeviceAccessHelper.findActiveBinding(
                parentDeviceBindingDao, parentUserId, invite.getDeviceId()) != null;
        vo.setAlreadyMember(alreadyMember);
        if (invalidReason != null) {
            vo.setValid(false);
            vo.setReason(invalidReason);
            return vo;
        }
        vo.setValid(true);
        vo.setReason(null);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceInviteAcceptVO accept(Long parentUserId, DeviceInviteAcceptDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getInviteToken())) {
            throw new RenException(ErrorCode.PARENT_INVITE_INVALID);
        }
        DeviceInviteEntity invite = requireValidInvite(dto.getInviteToken().trim());
        String deviceId = resolveInviteDeviceId(invite);

        if (invite.getInviterParentId().equals(parentUserId)) {
            throw new RenException(ErrorCode.PARENT_INVITE_SELF);
        }

        ParentDeviceBindingEntity existing = ParentDeviceAccessHelper.findActiveBinding(
                parentDeviceBindingDao, parentUserId, deviceId);
        if (existing != null) {
            DeviceInviteAcceptVO vo = new DeviceInviteAcceptVO();
            fillAcceptDeviceInfo(vo, deviceId);
            vo.setRole(existing.getRole());
            vo.setMessage("您已在该设备中");
            vo.setAlreadyMember(true);
            return vo;
        }

        Date now = new Date();
        ParentDeviceBindingEntity anyBinding =
                ParentDeviceAccessHelper.findAnyBinding(parentDeviceBindingDao, parentUserId, deviceId);
        if (anyBinding != null) {
            anyBinding.setDeviceId(deviceId);
            anyBinding.setRole(ParentDeviceBindingEntity.ROLE_MEMBER);
            anyBinding.setIsPrimary(0);
            anyBinding.setInvitedBy(invite.getInviterParentId());
            anyBinding.setStatus(ParentDeviceBindingEntity.STATUS_ACTIVE);
            anyBinding.setBindTime(now);
            anyBinding.setBindSource("invite");
            anyBinding.setUpdatedAt(now);
            ParentDeviceAccessHelper.applyRiskNotifyDefaults(anyBinding);
            parentDeviceBindingDao.updateById(anyBinding);
        } else {
            ParentDeviceBindingEntity binding = new ParentDeviceBindingEntity();
            binding.setParentUserId(parentUserId);
            binding.setDeviceId(deviceId);
            binding.setBindTime(now);
            binding.setBindSource("invite");
            binding.setRole(ParentDeviceBindingEntity.ROLE_MEMBER);
            binding.setIsPrimary(0);
            binding.setInvitedBy(invite.getInviterParentId());
            binding.setStatus(ParentDeviceBindingEntity.STATUS_ACTIVE);
            binding.setCreateTime(now);
            binding.setUpdatedAt(now);
            ParentDeviceAccessHelper.applyRiskNotifyDefaults(binding);
            parentDeviceBindingDao.insert(binding);
        }

        int used = invite.getUsedCount() == null ? 0 : invite.getUsedCount();
        used++;
        invite.setUsedCount(used);
        if (used >= (invite.getMaxUses() == null ? INVITE_MAX_USES : invite.getMaxUses())) {
            invite.setStatus(DeviceInviteEntity.STATUS_EXHAUSTED);
        }
        deviceInviteDao.updateById(invite);

        log.info("device invite accepted: deviceId={}, member={}, inviteId={}",
                deviceId, parentUserId, invite.getId());

        DeviceInviteAcceptVO vo = new DeviceInviteAcceptVO();
        fillAcceptDeviceInfo(vo, deviceId);
        vo.setRole(ParentDeviceBindingEntity.ROLE_MEMBER);
        vo.setMessage("加入成功");
        vo.setAlreadyMember(false);
        return vo;
    }

    private void fillAcceptDeviceInfo(DeviceInviteAcceptVO vo, String deviceId) {
        String canonicalDeviceId = ParentDeviceDisplayResolver.canonicalDeviceId(deviceDao, deviceId);
        vo.setDeviceId(canonicalDeviceId);
        vo.setDeviceName(ParentDeviceDisplayResolver.resolveDeviceName(
                deviceDao, deviceChildDao, parentDeviceBindingDao, canonicalDeviceId));
    }

    @Override
    public List<DeviceMemberItemVO> listMembers(Long parentUserId, String deviceId) {
        String resolvedDeviceId = ParentDeviceAccessHelper.requireActiveBinding(
                parentDeviceBindingDao, parentUserId, deviceId).getDeviceId();
        List<ParentDeviceBindingEntity> bindings =
                ParentDeviceAccessHelper.findActiveBindingsForDevice(parentDeviceBindingDao, resolvedDeviceId);
        List<DeviceMemberItemVO> result = new ArrayList<>();
        for (ParentDeviceBindingEntity b : bindings) {
            DeviceMemberItemVO item = new DeviceMemberItemVO();
            item.setParentId(b.getParentUserId());
            ParentUserEntity user = parentUserDao.selectById(b.getParentUserId());
            item.setNickname(ParentUserProfileHelper.resolveNickname(user));
            item.setAvatarUrl(ParentUserProfileHelper.resolveSharingAvatarUrl(user, parentStorageService));
            item.setRole(b.getRole());
            item.setIsPrimary(b.getIsPrimary() != null && b.getIsPrimary() == 1);
            item.setInvitedBy(b.getInvitedBy());
            item.setJoinedAt(b.getBindTime());
            boolean owner = ParentDeviceAccessHelper.isOwner(b);
            item.setReceiveRiskNotify(ParentDeviceAccessHelper.isReceiveRiskNotifyEnabled(b));
            item.setCanEdit(!owner);
            result.add(item);
        }
        return result;
    }

    @Override
    public List<String> listSharingMemberAvatars(Long parentUserId) {
        List<ParentDeviceBindingEntity> myBindings = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .orderByAsc(ParentDeviceBindingEntity::getBindTime));
        if (myBindings.isEmpty()) {
            return List.of();
        }

        Set<String> processedDeviceKeys = new HashSet<>();
        Map<Long, Date> coMemberJoinedAt = new LinkedHashMap<>();
        for (ParentDeviceBindingEntity myBinding : myBindings) {
            String canonicalDeviceId = resolveCanonicalDeviceId(myBinding.getDeviceId());
            String deviceKey = ParentDeviceAccessHelper.normalizeDeviceId(canonicalDeviceId);
            if (!processedDeviceKeys.add(deviceKey)) {
                continue;
            }
            List<ParentDeviceBindingEntity> deviceBindings = ParentDeviceAccessHelper.findActiveBindingsForDevice(
                    parentDeviceBindingDao, canonicalDeviceId);
            Map<Long, ParentDeviceBindingEntity> uniqueOnDevice = new LinkedHashMap<>();
            for (ParentDeviceBindingEntity binding : deviceBindings) {
                Long otherParentId = binding.getParentUserId();
                if (otherParentId == null || otherParentId.equals(parentUserId)) {
                    continue;
                }
                uniqueOnDevice.merge(otherParentId, binding, (existing, incoming) -> {
                    Date existingTime = existing.getBindTime();
                    Date incomingTime = incoming.getBindTime();
                    if (existingTime == null) {
                        return incoming;
                    }
                    if (incomingTime == null) {
                        return existing;
                    }
                    return existingTime.before(incomingTime) ? existing : incoming;
                });
            }
            for (Map.Entry<Long, ParentDeviceBindingEntity> entry : uniqueOnDevice.entrySet()) {
                Date joinedAt = entry.getValue().getBindTime();
                coMemberJoinedAt.merge(entry.getKey(), joinedAt, (existing, incoming) -> {
                    if (existing == null) {
                        return incoming;
                    }
                    if (incoming == null) {
                        return existing;
                    }
                    return existing.before(incoming) ? existing : incoming;
                });
            }
        }

        List<Long> sortedParentIds = coMemberJoinedAt.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getValue, Comparator.nullsLast(Date::compareTo)))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> avatars = new ArrayList<>();
        for (Long otherParentId : sortedParentIds) {
            if (avatars.size() >= 3) {
                break;
            }
            ParentUserEntity user = parentUserDao.selectById(otherParentId);
            String avatarUrl = ParentUserProfileHelper.resolveSharingAvatarUrl(user, parentStorageService);
            if (StringUtils.isNotBlank(avatarUrl)) {
                avatars.add(avatarUrl);
            }
        }
        return avatars;
    }

    private String resolveCanonicalDeviceId(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return deviceId;
        }
        DeviceEntity device = deviceDao.selectById(deviceId);
        if (device == null) {
            device = deviceDao.selectByIdOrMacVariant(deviceId);
        }
        return device != null ? device.getId() : deviceId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long ownerParentUserId, String deviceId, Long targetParentId) {
        ParentDeviceAccessHelper.requireOwner(parentDeviceBindingDao, ownerParentUserId, deviceId);
        if (targetParentId == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_CANNOT_REMOVE);
        }
        if (targetParentId.equals(ownerParentUserId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_CANNOT_REMOVE);
        }
        String resolvedDeviceId = ParentDeviceAccessHelper.requireActiveBinding(
                parentDeviceBindingDao, ownerParentUserId, deviceId).getDeviceId();
        ParentDeviceBindingEntity target = ParentDeviceAccessHelper.findActiveBinding(
                parentDeviceBindingDao, targetParentId, resolvedDeviceId);
        if (target == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_CANNOT_REMOVE);
        }
        if (ParentDeviceAccessHelper.isOwner(target)
                && target.getIsPrimary() != null && target.getIsPrimary() == 1) {
            throw new RenException(ErrorCode.PARENT_DEVICE_CANNOT_REMOVE);
        }
        Date now = new Date();
        target.setStatus(ParentDeviceBindingEntity.STATUS_REMOVED);
        target.setUpdatedAt(now);
        parentDeviceBindingDao.updateById(target);
        log.info("device member removed: deviceId={}, target={}, by={}",
                resolvedDeviceId, targetParentId, ownerParentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void leave(Long parentUserId, DeviceMemberLeaveDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getDeviceId())) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ParentDeviceBindingEntity binding = ParentDeviceAccessHelper.requireActiveBinding(
                parentDeviceBindingDao, parentUserId, dto.getDeviceId());
        if (ParentDeviceAccessHelper.isOwner(binding)) {
            throw new RenException("设备管理员请使用解绑设备");
        }
        Date now = new Date();
        binding.setStatus(ParentDeviceBindingEntity.STATUS_REMOVED);
        binding.setUpdatedAt(now);
        parentDeviceBindingDao.updateById(binding);
        log.info("device member leave: deviceId={}, parent={}", binding.getDeviceId(), parentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long parentUserId, DeviceInviteRevokeDTO dto) {
        if (dto == null) {
            throw new RenException(ErrorCode.PARENT_INVITE_INVALID);
        }
        DeviceInviteEntity invite = null;
        if (StringUtils.isNotBlank(dto.getInviteToken())) {
            invite = findInviteByToken(dto.getInviteToken().trim());
        } else if (dto.getInviteId() != null) {
            invite = deviceInviteDao.selectById(dto.getInviteId());
        }
        if (invite == null) {
            throw new RenException(ErrorCode.PARENT_INVITE_INVALID);
        }
        ParentDeviceAccessHelper.requireOwner(parentDeviceBindingDao, parentUserId, invite.getDeviceId());
        if (!DeviceInviteEntity.STATUS_ACTIVE.equals(invite.getStatus())) {
            throw new RenException(ErrorCode.PARENT_INVITE_INVALID);
        }
        invite.setStatus(DeviceInviteEntity.STATUS_REVOKED);
        invite.setRevokedAt(new Date());
        deviceInviteDao.updateById(invite);
        log.info("device invite revoked: inviteId={}, by={}", invite.getId(), parentUserId);
    }

    @Override
    public List<DeviceInviteItemVO> listInvites(Long parentUserId, String deviceId, String status) {
        ParentDeviceBindingEntity ownerBinding =
                ParentDeviceAccessHelper.requireOwner(parentDeviceBindingDao, parentUserId, deviceId);
        String resolvedDeviceId = ownerBinding.getDeviceId();
        LambdaQueryWrapper<DeviceInviteEntity> wrapper = new LambdaQueryWrapper<DeviceInviteEntity>()
                .eq(DeviceInviteEntity::getDeviceId, resolvedDeviceId)
                .eq(DeviceInviteEntity::getInviterParentId, parentUserId)
                .orderByDesc(DeviceInviteEntity::getCreatedAt);
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(DeviceInviteEntity::getStatus, status.trim());
        }
        List<DeviceInviteEntity> list = deviceInviteDao.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(i -> {
            DeviceInviteItemVO vo = new DeviceInviteItemVO();
            vo.setInviteId(i.getId());
            vo.setDeviceId(i.getDeviceId());
            vo.setExpiresAt(i.getExpiresAt());
            vo.setMaxUses(i.getMaxUses());
            vo.setUsedCount(i.getUsedCount());
            vo.setStatus(resolveDisplayStatus(i));
            vo.setCreatedAt(i.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    private void checkInviteRateLimit(String deviceId) {
        String key = RedisKeys.getDeviceInviteRateLimitKey(deviceId);
        Long count = redisUtils.increment(key, TimeUnit.HOURS.toSeconds(1));
        if (count != null && count > INVITE_RATE_LIMIT_PER_HOUR) {
            throw new RenException(ErrorCode.SMS_SEND_TOO_FREQUENTLY, "邀请过于频繁，请稍后再试");
        }
    }

    private static String generateInviteToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        return HashEncryptionUtil.hexDigest(token, "SHA-256");
    }

    private DeviceInviteEntity findInviteByToken(String inviteToken) {
        if (StringUtils.isBlank(inviteToken)) {
            return null;
        }
        return deviceInviteDao.selectOne(
                new LambdaQueryWrapper<DeviceInviteEntity>()
                        .eq(DeviceInviteEntity::getTokenHash, hashToken(inviteToken.trim()))
                        .last("LIMIT 1"));
    }

    private DeviceInviteEntity requireValidInvite(String inviteToken) {
        DeviceInviteEntity invite = findInviteByToken(inviteToken);
        if (invite == null) {
            throw new RenException(ErrorCode.PARENT_INVITE_INVALID);
        }
        String reason = resolveInvalidReason(invite);
        if ("expired".equals(reason)) {
            markExpiredIfNeeded(invite);
            throw new RenException(ErrorCode.PARENT_INVITE_EXPIRED);
        }
        if ("revoked".equals(reason)) {
            throw new RenException(ErrorCode.PARENT_INVITE_INVALID);
        }
        if ("exhausted".equals(reason)) {
            throw new RenException(ErrorCode.PARENT_INVITE_EXHAUSTED);
        }
        if (reason != null) {
            throw new RenException(ErrorCode.PARENT_INVITE_INVALID);
        }
        if (ParentDeviceAccessHelper.findPrimaryOwner(parentDeviceBindingDao, invite.getDeviceId()) == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND, "设备不存在或已解绑");
        }
        return invite;
    }

    private String resolveInvalidReason(DeviceInviteEntity invite) {
        if (invite == null) {
            return "not_found";
        }
        if (DeviceInviteEntity.STATUS_REVOKED.equals(invite.getStatus())) {
            return "revoked";
        }
        if (DeviceInviteEntity.STATUS_EXHAUSTED.equals(invite.getStatus())) {
            return "exhausted";
        }
        if (DeviceInviteEntity.STATUS_EXPIRED.equals(invite.getStatus())) {
            return "expired";
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().before(new Date())) {
            return "expired";
        }
        int maxUses = invite.getMaxUses() == null ? INVITE_MAX_USES : invite.getMaxUses();
        int used = invite.getUsedCount() == null ? 0 : invite.getUsedCount();
        if (used >= maxUses) {
            return "exhausted";
        }
        if (!DeviceInviteEntity.STATUS_ACTIVE.equals(invite.getStatus())) {
            return "not_found";
        }
        return null;
    }

    private void markExpiredIfNeeded(DeviceInviteEntity invite) {
        if (invite == null || DeviceInviteEntity.STATUS_EXPIRED.equals(invite.getStatus())) {
            return;
        }
        deviceInviteDao.update(null, new LambdaUpdateWrapper<DeviceInviteEntity>()
                .eq(DeviceInviteEntity::getId, invite.getId())
                .set(DeviceInviteEntity::getStatus, DeviceInviteEntity.STATUS_EXPIRED));
    }

    private static String resolveDisplayStatus(DeviceInviteEntity invite) {
        String reason = resolveInvalidReasonStatic(invite);
        if ("expired".equals(reason)) {
            return DeviceInviteEntity.STATUS_EXPIRED;
        }
        if ("exhausted".equals(reason)) {
            return DeviceInviteEntity.STATUS_EXHAUSTED;
        }
        return invite.getStatus();
    }

    private static String resolveInvalidReasonStatic(DeviceInviteEntity invite) {
        if (invite == null) {
            return "not_found";
        }
        if (DeviceInviteEntity.STATUS_REVOKED.equals(invite.getStatus())) {
            return "revoked";
        }
        if (DeviceInviteEntity.STATUS_EXHAUSTED.equals(invite.getStatus())) {
            return "exhausted";
        }
        if (DeviceInviteEntity.STATUS_EXPIRED.equals(invite.getStatus())) {
            return "expired";
        }
        if (invite.getExpiresAt() != null && invite.getExpiresAt().before(new Date())) {
            return "expired";
        }
        int maxUses = invite.getMaxUses() == null ? INVITE_MAX_USES : invite.getMaxUses();
        int used = invite.getUsedCount() == null ? 0 : invite.getUsedCount();
        if (used >= maxUses) {
            return "exhausted";
        }
        return null;
    }

    private static Date addDays(Date base, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(base);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }

    private String resolveInviteDeviceId(DeviceInviteEntity invite) {
        ParentDeviceBindingEntity ownerBinding = findOwnerBindingForInvite(invite);
        if (ownerBinding != null && StringUtils.isNotBlank(ownerBinding.getDeviceId())) {
            DeviceEntity device = ParentDeviceDisplayResolver.resolveDevice(deviceDao, ownerBinding.getDeviceId());
            if (device != null) {
                return device.getId();
            }
            return ownerBinding.getDeviceId();
        }
        return ParentDeviceDisplayResolver.canonicalDeviceId(deviceDao, invite.getDeviceId());
    }

    private ParentDeviceBindingEntity findOwnerBindingForInvite(DeviceInviteEntity invite) {
        if (invite == null || invite.getInviterParentId() == null) {
            return null;
        }
        List<ParentDeviceBindingEntity> ownerBindings = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, invite.getInviterParentId())
                        .eq(ParentDeviceBindingEntity::getStatus, ParentDeviceBindingEntity.STATUS_ACTIVE)
                        .eq(ParentDeviceBindingEntity::getRole, ParentDeviceBindingEntity.ROLE_OWNER));
        if (ownerBindings.isEmpty()) {
            return null;
        }
        for (ParentDeviceBindingEntity ownerBinding : ownerBindings) {
            if (ParentDeviceAccessHelper.deviceIdsEquivalent(ownerBinding.getDeviceId(), invite.getDeviceId())) {
                return ownerBinding;
            }
        }
        if (ownerBindings.size() == 1) {
            return ownerBindings.get(0);
        }
        return ParentDeviceAccessHelper.findPrimaryOwner(parentDeviceBindingDao, invite.getDeviceId());
    }

    @Override
    public void revokeActiveInvitesForDevice(String deviceId) {
        deviceInviteDao.update(null, new LambdaUpdateWrapper<DeviceInviteEntity>()
                .eq(DeviceInviteEntity::getDeviceId, deviceId)
                .eq(DeviceInviteEntity::getStatus, DeviceInviteEntity.STATUS_ACTIVE)
                .set(DeviceInviteEntity::getStatus, DeviceInviteEntity.STATUS_REVOKED)
                .set(DeviceInviteEntity::getRevokedAt, new Date()));
    }
}
