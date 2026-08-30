-- 硬件 OTA 专用 OSS Bucket（空则回退 aliyun.oss.bucket，不影响家长端 xiaozhi-parent）
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'aliyun.oss.ota.bucket', '', 'string', 1,
       '硬件 OTA SWU 专用 OSS Bucket。留空则回退 aliyun.oss.bucket；建议单独建桶，勿改家长端 xiaozhi-parent'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'aliyun.oss.ota.bucket');
