-- 更新设备未同意协议时的 TTS 文案（独立 changeset，勿再改 202607121500.sql）
UPDATE `sys_params`
SET `param_value` = '请先由主账号家长在小程序中阅读并同意儿童隐私保护说明。同意后设备才能继续使用，本次对话即将结束。',
    `remark`      = '主账号未同意协议时设备 TTS 播报文案（xiaozhi-server 通过 getConfig 拉取）。'
WHERE `param_code` = 'consent_blocked.prompt';
