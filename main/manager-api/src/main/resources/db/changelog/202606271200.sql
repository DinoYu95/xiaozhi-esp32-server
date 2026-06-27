-- 设备未绑定时的 TTS 引导文案（智控台参数管理可改，xiaozhi-server 通过 getConfig 拉取）
DELETE FROM `sys_params` WHERE param_code = 'device_bind_prompt.prompt';
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'device_bind_prompt.prompt',
       '请打开智伴未来微信小程序扫码绑定设备',
       'string',
       1,
       '设备未绑定时 TTS 播报文案，引导家长打开小程序扫码绑定';
