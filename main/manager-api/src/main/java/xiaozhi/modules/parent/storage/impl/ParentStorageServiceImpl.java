package xiaozhi.modules.parent.storage.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.parent.storage.ParentStorageCategory;
import xiaozhi.modules.parent.storage.ParentStorageService;
import xiaozhi.modules.parent.storage.vo.ParentStorageUploadVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParentStorageServiceImpl implements ParentStorageService {

    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Pattern LOCAL_FILENAME_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|jpeg|png|gif|webp)$",
            Pattern.CASE_INSENSITIVE);

    private static final String PARAM_ENABLED = "aliyun.oss.enabled";
    private static final String PARAM_ENDPOINT = "aliyun.oss.endpoint";
    private static final String PARAM_BUCKET = "aliyun.oss.bucket";
    private static final String PARAM_ACCESS_KEY_ID = "aliyun.oss.access_key_id";
    private static final String PARAM_ACCESS_KEY_SECRET = "aliyun.oss.access_key_secret";
    private static final String PARAM_CDN_DOMAIN = "aliyun.oss.cdn_domain";
    private static final String PARAM_PATH_PREFIX = "aliyun.oss.path_prefix";
    private static final String PARAM_PUBLIC_READ = "aliyun.oss.public_read";
    private static final String PARAM_SIGNED_EXPIRE = "aliyun.oss.signed_url_expire_seconds";

    private final SysParamsService sysParamsService;

    @Value("${xiaozhi.parent.public-base-url:}")
    private String parentPublicBaseUrlFromConfig;

    @Override
    public boolean isOssEnabled() {
        return loadOssConfig().enabled;
    }

    @Override
    public ParentStorageUploadVO upload(ParentStorageCategory category, Long parentUserId, MultipartFile file,
            String publicBaseUrl) {
        validateImageFile(category, file);
        String ext = resolveImageExtension(file);
        OssConfig cfg = loadOssConfig();
        if (cfg.enabled) {
            return uploadToOss(category, parentUserId, file, ext, cfg);
        }
        return uploadToLocal(category, parentUserId, file, ext, publicBaseUrl);
    }

    @Override
    public String resolveAccessUrl(String storedReference) {
        return resolveAccessUrl(null, storedReference);
    }

    @Override
    public String resolveAccessUrl(ParentStorageCategory category, String storedReference) {
        if (StringUtils.isBlank(storedReference)) {
            return null;
        }
        String ref = storedReference.trim();
        if (ref.startsWith("http://") || ref.startsWith("https://")) {
            return ref;
        }
        OssConfig cfg = loadOssConfig();
        if (cfg.enabled && isOssObjectKey(ref, cfg)) {
            return buildOssAccessUrl(ref, cfg);
        }
        if (LOCAL_FILENAME_PATTERN.matcher(ref).matches() && category != null) {
            String base = trimSlash(parentPublicBaseUrlFromConfig);
            if (StringUtils.isBlank(base)) {
                return null;
            }
            String path = category == ParentStorageCategory.FEEDBACK
                    ? "/parent-api/feedback/image/file/" + ref
                    : "/parent-api/auth/avatar/file/" + ref;
            return base + path;
        }
        return ref;
    }

    @Override
    public String normalizeAndValidate(Long parentUserId, ParentStorageCategory category, String objectKeyOrUrl) {
        if (StringUtils.isBlank(objectKeyOrUrl)) {
            throw new RenException(ErrorCode.PARENT_STORAGE_OBJECT_INVALID);
        }
        String ref = objectKeyOrUrl.trim();
        OssConfig cfg = loadOssConfig();

        if (ref.startsWith("http://") || ref.startsWith("https://")) {
            String objectKey = extractObjectKeyFromUrl(ref, cfg);
            if (objectKey != null) {
                ref = objectKey;
            } else if (isLegacyApiUrl(ref)) {
                String filename = extractLegacyFilename(ref);
                if (filename != null && LOCAL_FILENAME_PATTERN.matcher(filename).matches()) {
                    ref = filename;
                } else {
                    throw new RenException(ErrorCode.PARENT_STORAGE_OBJECT_INVALID);
                }
            } else {
                throw new RenException(ErrorCode.PARENT_STORAGE_OBJECT_INVALID);
            }
        }

        if (cfg.enabled && isOssObjectKey(ref, cfg)) {
            assertOwnedObjectKey(parentUserId, category, ref, cfg);
            return ref;
        }
        if (LOCAL_FILENAME_PATTERN.matcher(ref).matches()) {
            return ref;
        }
        throw new RenException(ErrorCode.PARENT_STORAGE_OBJECT_INVALID);
    }

    @Override
    public byte[] readLocalFile(ParentStorageCategory category, String filename) {
        if (!LOCAL_FILENAME_PATTERN.matcher(filename).matches()) {
            return null;
        }
        String subDir = category == ParentStorageCategory.FEEDBACK ? "parent-feedback" : "parent-avatar";
        Path dirAbs = Paths.get("uploadfile", subDir).toAbsolutePath().normalize();
        Path file = dirAbs.resolve(filename).normalize();
        if (!file.startsWith(dirAbs) || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.warn("读取本地文件失败: {}", filename, e);
            return null;
        }
    }

    private ParentStorageUploadVO uploadToOss(ParentStorageCategory category, Long parentUserId, MultipartFile file,
            String ext, OssConfig cfg) {
        validateOssConfig(cfg);
        String objectKey = buildObjectKey(category, parentUserId, ext, cfg);
        OSS client = null;
        try (InputStream in = file.getInputStream()) {
            client = new OSSClientBuilder().build(cfg.endpoint, cfg.accessKeyId, cfg.accessKeySecret);
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            if (StringUtils.isNotBlank(file.getContentType())) {
                meta.setContentType(file.getContentType());
            }
            client.putObject(cfg.bucket, objectKey, in, meta);
        } catch (IOException e) {
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR, e);
        } catch (Exception e) {
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR, e);
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
        ParentStorageUploadVO vo = new ParentStorageUploadVO();
        vo.setCategory(category.getCode());
        vo.setObjectKey(objectKey);
        vo.setAccessUrl(buildOssAccessUrl(objectKey, cfg));
        vo.setOss(true);
        return vo;
    }

    private ParentStorageUploadVO uploadToLocal(ParentStorageCategory category, Long parentUserId, MultipartFile file,
            String ext, String publicBaseUrl) {
        String storedName = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + "." + ext;
        String subDir = category == ParentStorageCategory.FEEDBACK ? "parent-feedback" : "parent-avatar";
        Path dirAbs = Paths.get("uploadfile", subDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dirAbs);
            Path target = dirAbs.resolve(storedName).normalize();
            if (!target.startsWith(dirAbs)) {
                throw new RenException(ErrorCode.UPLOAD_FILE_ERROR);
            }
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new RenException(ErrorCode.UPLOAD_FILE_ERROR, e);
        }
        String accessPath = category == ParentStorageCategory.FEEDBACK
                ? "/parent-api/feedback/image/file/" + storedName
                : "/parent-api/auth/avatar/file/" + storedName;
        String base = trimSlash(publicBaseUrl);
        ParentStorageUploadVO vo = new ParentStorageUploadVO();
        vo.setCategory(category.getCode());
        vo.setObjectKey(storedName);
        vo.setAccessUrl(StringUtils.isNotBlank(base) ? base + accessPath : accessPath);
        vo.setOss(false);
        return vo;
    }

    private String buildOssAccessUrl(String objectKey, OssConfig cfg) {
        if (cfg.publicRead && StringUtils.isNotBlank(cfg.cdnDomain)) {
            return "https://" + trimSlash(cfg.cdnDomain) + "/" + objectKey;
        }
        if (cfg.publicRead) {
            return "https://" + cfg.bucket + "." + cfg.endpoint + "/" + objectKey;
        }
        OSS client = null;
        try {
            client = new OSSClientBuilder().build(cfg.endpoint, cfg.accessKeyId, cfg.accessKeySecret);
            Date expiration = new Date(System.currentTimeMillis() + cfg.signedUrlExpireSeconds * 1000L);
            URL url = client.generatePresignedUrl(cfg.bucket, objectKey, expiration);
            return url != null ? url.toString() : null;
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    private void assertOwnedObjectKey(Long parentUserId, ParentStorageCategory category, String objectKey,
            OssConfig cfg) {
        String prefix = normalizePathPrefix(cfg.pathPrefix);
        String expectedSegment = prefix + "/" + category.getPathSegment() + "/";
        if (!objectKey.startsWith(expectedSegment)) {
            throw new RenException(ErrorCode.PARENT_STORAGE_OBJECT_INVALID);
        }
        String tail = objectKey.substring(expectedSegment.length());
        String[] parts = tail.split("/");
        if (parts.length < 3) {
            throw new RenException(ErrorCode.PARENT_STORAGE_OBJECT_INVALID);
        }
        if (!String.valueOf(parentUserId).equals(parts[1])) {
            throw new RenException(ErrorCode.PARENT_STORAGE_OBJECT_INVALID);
        }
    }

    private String buildObjectKey(ParentStorageCategory category, Long parentUserId, String ext, OssConfig cfg) {
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String uuid = UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
        return normalizePathPrefix(cfg.pathPrefix) + "/" + category.getPathSegment() + "/" + month + "/"
                + parentUserId + "/" + uuid + "." + ext;
    }

    private String extractObjectKeyFromUrl(String url, OssConfig cfg) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (StringUtils.isBlank(path)) {
                return null;
            }
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            String prefix = normalizePathPrefix(cfg.pathPrefix);
            if (path.startsWith(prefix + "/parent/")) {
                return path;
            }
            if (StringUtils.isNotBlank(cfg.cdnDomain)) {
                String host = uri.getHost();
                if (host != null && host.equalsIgnoreCase(trimSlash(cfg.cdnDomain)) && path.startsWith(prefix + "/")) {
                    return path;
                }
            }
            String ossHost = cfg.bucket + "." + cfg.endpoint;
            if (hostEquals(uri.getHost(), ossHost) && path.startsWith(prefix + "/")) {
                return path;
            }
        } catch (Exception e) {
            log.debug("无法从 URL 解析 objectKey: {}", url);
        }
        return null;
    }

    private static boolean hostEquals(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private static boolean isLegacyApiUrl(String url) {
        return url.contains("/parent-api/auth/avatar/file/")
                || url.contains("/parent-api/feedback/image/file/");
    }

    private static String extractLegacyFilename(String url) {
        int idx = Math.max(url.lastIndexOf("/parent-api/auth/avatar/file/"),
                url.lastIndexOf("/parent-api/feedback/image/file/"));
        if (idx < 0) {
            return null;
        }
        int start = url.indexOf('/', idx + 1);
        while (start >= 0 && start < url.length() - 1) {
            int next = url.indexOf('/', start + 1);
            String segment = next < 0 ? url.substring(start + 1) : url.substring(start + 1, next);
            if (LOCAL_FILENAME_PATTERN.matcher(segment).matches()) {
                return segment;
            }
            if (next < 0) {
                break;
            }
            start = next;
        }
        String[] parts = url.split("/");
        if (parts.length > 0) {
            return parts[parts.length - 1];
        }
        return null;
    }

    private boolean isOssObjectKey(String ref, OssConfig cfg) {
        String prefix = normalizePathPrefix(cfg.pathPrefix);
        return ref.startsWith(prefix + "/parent/");
    }

    private void validateImageFile(ParentStorageCategory category, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.UPLOAD_FILE_EMPTY);
        }
        if (file.getSize() > category.getMaxBytes()) {
            throw new RenException(category == ParentStorageCategory.AVATAR
                    ? "头像文件不能超过 2MB" : "截图不能超过 5MB");
        }
        if (resolveImageExtension(file) == null) {
            throw new RenException("仅支持 jpg、jpeg、png、gif、webp 图片");
        }
    }

    private static String resolveImageExtension(MultipartFile file) {
        String ext = resolveImageExtensionFromName(file.getOriginalFilename());
        if (ext != null) {
            return ext;
        }
        return resolveImageExtensionFromContentType(file.getContentType());
    }

    private static String resolveImageExtensionFromName(String originalFilename) {
        if (StringUtils.isBlank(originalFilename) || !originalFilename.contains(".")) {
            return null;
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXT.contains(ext) ? ext : null;
    }

    private static String resolveImageExtensionFromContentType(String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return null;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("png")) {
            return "png";
        }
        if (ct.contains("gif")) {
            return "gif";
        }
        if (ct.contains("webp")) {
            return "webp";
        }
        if (ct.contains("jpeg") || ct.contains("jpg")) {
            return "jpg";
        }
        return null;
    }

    private void validateOssConfig(OssConfig cfg) {
        if (StringUtils.isAnyBlank(cfg.endpoint, cfg.bucket, cfg.accessKeyId, cfg.accessKeySecret)) {
            throw new RenException("OSS 未配置完整，请在参数字典填写 aliyun.oss.*");
        }
    }

    private OssConfig loadOssConfig() {
        OssConfig cfg = new OssConfig();
        cfg.enabled = "true".equalsIgnoreCase(StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_ENABLED, true)));
        cfg.endpoint = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_ENDPOINT, true));
        cfg.bucket = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_BUCKET, true));
        cfg.accessKeyId = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_ACCESS_KEY_ID, true));
        cfg.accessKeySecret = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_ACCESS_KEY_SECRET, true));
        cfg.cdnDomain = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_CDN_DOMAIN, true));
        String prefix = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_PATH_PREFIX, true));
        cfg.pathPrefix = StringUtils.isNotBlank(prefix) ? prefix : "xiaozhi";
        cfg.publicRead = !"false".equalsIgnoreCase(
                StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_PUBLIC_READ, true)));
        try {
            cfg.signedUrlExpireSeconds = Integer.parseInt(
                    StringUtils.defaultIfBlank(sysParamsService.getValue(PARAM_SIGNED_EXPIRE, true), "86400"));
        } catch (NumberFormatException e) {
            cfg.signedUrlExpireSeconds = 86400;
        }
        return cfg;
    }

    private static String normalizePathPrefix(String prefix) {
        String p = StringUtils.trimToEmpty(prefix);
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static final class OssConfig {
        boolean enabled;
        String endpoint;
        String bucket;
        String accessKeyId;
        String accessKeySecret;
        String cdnDomain;
        String pathPrefix;
        boolean publicRead = true;
        int signedUrlExpireSeconds = 86400;
    }
}
