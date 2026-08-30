package xiaozhi.modules.ota.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.ota.service.OtaPackageStorageService;
import xiaozhi.modules.ota.util.SwuFilenameParser;
import xiaozhi.modules.sys.service.SysParamsService;

/**
 * SWU 上传：复用 ParentStorage 的 aliyun.oss.* 参数字典。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OtaPackageStorageServiceImpl implements OtaPackageStorageService {

    private static final String PARAM_ENABLED = "aliyun.oss.enabled";
    private static final String PARAM_ENDPOINT = "aliyun.oss.endpoint";
    private static final String PARAM_BUCKET = "aliyun.oss.bucket";
    private static final String PARAM_OTA_BUCKET = "aliyun.oss.ota.bucket";
    private static final String PARAM_ACCESS_KEY_ID = "aliyun.oss.access_key_id";
    private static final String PARAM_ACCESS_KEY_SECRET = "aliyun.oss.access_key_secret";
    private static final String PARAM_CDN_DOMAIN = "aliyun.oss.cdn_domain";
    private static final String PARAM_PATH_PREFIX = "aliyun.oss.path_prefix";
    private static final String PARAM_PUBLIC_READ = "aliyun.oss.public_read";
    private static final String PARAM_SIGNED_EXPIRE = "aliyun.oss.signed_url_expire_seconds";

    private final SysParamsService sysParamsService;

    @Value("${xiaozhi.parent.public-base-url:}")
    private String publicBaseUrl;

    @Override
    public StoredObject upload(SwuFilenameParser.ParsedSwu parsed, InputStream in, long size) {
        String logicalKey = SwuFilenameParser.ossKey(parsed);
        OssConfig cfg = loadOssConfig();
        if (cfg.enabled) {
            uploadToOss(logicalKey, in, size, cfg);
            return new StoredObject(logicalKey, true);
        }
        uploadToLocal(logicalKey, in);
        return new StoredObject(logicalKey, false);
    }

    @Override
    public String resolveAccessUrl(String ossKey) {
        if (StringUtils.isBlank(ossKey)) {
            return null;
        }
        if (ossKey.startsWith("http://") || ossKey.startsWith("https://")) {
            return ossKey;
        }
        OssConfig cfg = loadOssConfig();
        if (cfg.enabled) {
            return buildOssAccessUrl(physicalKey(ossKey, cfg), cfg);
        }
        String base = trimSlash(publicBaseUrl);
        String path = "/ota/swu/file/" + ossKey;
        return StringUtils.isNotBlank(base) ? base + path : path;
    }

    public byte[] readLocalFile(String ossKey) {
        Path file = localPath(ossKey);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.warn("读取本地 SWU 失败: {}", ossKey, e);
            return null;
        }
    }

    private void uploadToOss(String logicalKey, InputStream in, long size, OssConfig cfg) {
        if (StringUtils.isAnyBlank(cfg.endpoint, cfg.bucket, cfg.accessKeyId, cfg.accessKeySecret)) {
            throw new RenException("OSS 未配置完整，请在参数字典填写 aliyun.oss.*");
        }
        String objectKey = physicalKey(logicalKey, cfg);
        OSS client = null;
        try {
            client = new OSSClientBuilder().build(cfg.endpoint, cfg.accessKeyId, cfg.accessKeySecret);
            ObjectMetadata meta = new ObjectMetadata();
            if (size > 0) {
                meta.setContentLength(size);
            }
            meta.setContentType("application/octet-stream");
            client.putObject(cfg.bucket, objectKey, in, meta);
        } catch (Exception e) {
            throw new RenException(ErrorCode.OSS_UPLOAD_FILE_ERROR, e);
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    private void uploadToLocal(String logicalKey, InputStream in) {
        Path target = localPath(logicalKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RenException(ErrorCode.UPLOAD_FILE_ERROR, e);
        }
    }

    private Path localPath(String logicalKey) {
        Path root = Paths.get("uploadfile").toAbsolutePath().normalize();
        Path file = root.resolve(logicalKey).normalize();
        if (!file.startsWith(root)) {
            throw new RenException(ErrorCode.UPLOAD_FILE_ERROR);
        }
        return file;
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

    private static String physicalKey(String logicalKey, OssConfig cfg) {
        String prefix = normalizePathPrefix(cfg.pathPrefix);
        if (StringUtils.isBlank(prefix)) {
            return logicalKey;
        }
        return prefix + "/" + logicalKey;
    }

    private OssConfig loadOssConfig() {
        OssConfig cfg = new OssConfig();
        cfg.enabled = "true".equalsIgnoreCase(StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_ENABLED, true)));
        cfg.endpoint = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_ENDPOINT, true));
        String otaBucket = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_OTA_BUCKET, true));
        cfg.bucket = StringUtils.isNotBlank(otaBucket)
                ? otaBucket
                : StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_BUCKET, true));
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
