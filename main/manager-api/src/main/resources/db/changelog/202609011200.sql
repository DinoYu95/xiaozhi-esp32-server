-- 设备端 SWU 下载方式（presigned=OSS 预签名；proxy=经 manager-api 代理，适合嵌入式缺 CA 证书）
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.ota.device_download_mode', 'presigned', 'string', 1,
       '设备 SWU 下载：presigned=OSS 预签名 URL（私有桶推荐）；proxy=走 /ota/swu/file 由服务端转 OSS（需配置 xiaozhi.parent.public-base-url，设备 wget 无需校验 OSS 证书）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.ota.device_download_mode');
