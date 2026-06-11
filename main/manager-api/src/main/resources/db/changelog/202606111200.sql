-- 家长端文件存储（阿里云 OSS，未开启时仍走本地 uploadfile）
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.enabled', 'false', 'string', 1,
       '是否启用阿里云 OSS 存储家长端图片。true 时头像/反馈截图上传至 OSS；false 时仍使用本地 uploadfile。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.enabled');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.endpoint', '', 'string', 1,
       'OSS Endpoint，如 oss-cn-hangzhou.aliyuncs.com（不含 https://）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.endpoint');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.bucket', '', 'string', 1,
       'OSS Bucket 名称'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.bucket');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.access_key_id', '', 'string', 1,
       'OSS AccessKey ID（建议使用 RAM 子账号，仅授予 PutObject/GetObject 权限）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.access_key_id');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.access_key_secret', '', 'string', 1,
       'OSS AccessKey Secret'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.access_key_secret');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.cdn_domain', '', 'string', 1,
       '可选。绑定了 OSS 的 CDN 加速域名（不含协议），如 static.example.com。配置后 accessUrl 优先走 CDN。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.cdn_domain');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.path_prefix', 'xiaozhi', 'string', 1,
       'OSS 对象键前缀，实际路径如 xiaozhi/parent/avatar/202606/{parentUserId}/{uuid}.jpg'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.path_prefix');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.public_read', 'true', 'string', 1,
       'Bucket 或前缀是否公共读。true：返回 CDN/直链 URL；false：返回签名 URL（有效期见 signed_url_expire_seconds）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.public_read');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.signed_url_expire_seconds', '86400', 'string', 1,
       '私有 Bucket 时签名 URL 有效期（秒），默认 86400（24 小时）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.signed_url_expire_seconds');
