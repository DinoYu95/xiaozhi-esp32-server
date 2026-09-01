package xiaozhi.modules.ota.service;

import java.io.InputStream;

import xiaozhi.modules.ota.util.SwuFilenameParser;

public interface OtaPackageStorageService {

    StoredObject upload(SwuFilenameParser.ParsedSwu parsed, InputStream in, long size);

    String resolveAccessUrl(String ossKey);

    /** 设备端 SWU 下载 URL（私有桶默认预签名；可配置经 manager-api 代理） */
    String resolveDeviceDownloadUrl(String ossKey);

    byte[] readLocalFile(String ossKey);

    /** @return null 表示对象不存在 */
    SwuStream openSwuStream(String ossKey);

    record StoredObject(String ossKey, boolean oss) {
    }

    record SwuStream(InputStream inputStream, long contentLength, String filename, AutoCloseable cleanup)
            implements AutoCloseable {
        @Override
        public void close() {
            try {
                inputStream.close();
            } catch (Exception ignored) {
                // best-effort close
            }
            if (cleanup != null) {
                try {
                    cleanup.close();
                } catch (Exception ignored) {
                    // best-effort cleanup (e.g. OSS client shutdown)
                }
            }
        }
    }
}
