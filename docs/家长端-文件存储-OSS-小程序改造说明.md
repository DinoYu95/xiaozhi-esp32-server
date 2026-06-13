# 家长端「文件存储 / OSS」小程序改造说明

> 面向小程序开发：头像、反馈截图等图片的上传与展示改造。  
> 后端：`manager-api` `/parent-api/storage/*`，并改造 `/parent-api/auth/avatar`、`/parent-api/feedback/image`。  
> 运维：智控台 **参数管理** 配置 `aliyun.oss.*`（未开启时仍走本地 `uploadfile`，接口形态不变）。

---

## 一、改造背景

| 改造前 | 改造后 |
|--------|--------|
| 图片落在 API 服务器本地 `uploadfile/` | 开启 OSS 后图片落在 **阿里云 OSS** |
| 返回 URL 形如 `.../parent-api/auth/avatar/file/uuid.jpg` | OSS 开启后返回 **OSS 直链** 或签名 URL |
| 各业务各自上传 | 新增 **统一上传接口** `POST /parent-api/storage/upload` |

**本次涉及的小程序页面/能力：**

- 家长 **头像**（我的 → 编辑资料 / 上传头像）
- 内测反馈 **截图**（`feedback/submit` 最多 3 张）

声纹、聊天语音等 **不在本次范围**，接口不变。

---

## 二、核心约定（必读）

### 2.1 推荐数据流

```
选图 → 调上传接口 → 拿到 objectKey + accessUrl
     → 预览用 accessUrl（<image src>）
     → 提交业务接口时传 objectKey（推荐）或 accessUrl
     → 列表/详情/我的信息 由后端 resolve 成可访问 URL，前端直接展示
```

### 2.2 上传返回值怎么用

| 字段 | 用途 |
|------|------|
| `objectKey` | **推荐**写入 `PUT /profile` 的 `avatarUrl`，或 `POST /feedback` 的 `imageUrls[]` |
| `accessUrl` | 仅用于**本地预览**（`<image>`），不要长期缓存；私有桶会过期 |
| `oss` | `true`=已传 OSS；`false`=仍本地盘（联调/未开 OSS） |

### 2.3 存储路径（OSS 开启时，仅供理解）

| category | OSS 路径示例 |
|----------|----------------|
| `avatar` | `xiaozhi/parent/avatar/202606/{parentUserId}/{uuid}.jpg` |
| `feedback` | `xiaozhi/parent/feedback/202606/{parentUserId}/{uuid}.jpg` |

### 2.4 图片限制

| category | 格式 | 大小上限 |
|----------|------|----------|
| `avatar` | jpg/jpeg/png/gif/webp | 2MB |
| `feedback` | jpg/jpeg/png/gif/webp | 5MB |

---

## 三、接口说明

**Base URL：** 与现有家长端一致，例如 `https://your-host/xiaozhi`（以实际部署为准）。

**鉴权：** 下列接口均需 Header：

```
Authorization: Bearer {家长登录 token}
```

**统一响应：**

```json
{ "code": 0, "msg": "success", "data": { } }
```

`code !== 0` 时展示 `msg`。

---

### 3.1 统一上传（推荐新接入用这个）

```
POST /parent-api/storage/upload
Content-Type: multipart/form-data
```

| 表单字段 | 类型 | 必填 | 说明 |
|----------|------|------|------|
| `category` | string | 是 | `avatar` 或 `feedback` |
| `file` | file | 是 | 图片文件 |

**响应 data：**

```json
{
  "category": "avatar",
  "objectKey": "xiaozhi/parent/avatar/202606/123/a1b2c3d4-e5f6-7890-abcd-ef1234567890.jpg",
  "accessUrl": "https://your-bucket.oss-cn-hangzhou.aliyuncs.com/xiaozhi/parent/avatar/202606/123/a1b2c3d4-....jpg",
  "oss": true
}
```

未开 OSS 时 `oss=false`，`accessUrl` 仍为可访问地址（走 API 本地文件路径），`objectKey` 为本地文件名（`uuid.jpg`）。

**错误码：**

| code | 含义 |
|------|------|
| 20001 | token 无效 |
| 20016 | `category` 无效（非 avatar/feedback） |
| 10021 | 文件为空 |
| 10022/10024 | 上传失败 |

---

### 3.2 兼容旧上传入口（可继续用，内部已走同一套存储）

与统一上传 **等价**，仅路径不同；返回值已增加 `objectKey`。

#### 头像

```
POST /parent-api/auth/avatar
Content-Type: multipart/form-data
字段名: file
```

**响应 data：**

```json
{
  "objectKey": "xiaozhi/parent/avatar/202606/123/....jpg",
  "avatarUrl": "https://....jpg"
}
```

> `avatarUrl` 与统一上传的 `accessUrl` 含义相同。

#### 反馈截图

```
POST /parent-api/feedback/image
Content-Type: multipart/form-data
字段名: file
```

须已登录且为内测用户（否则 `20008`）。

**响应 data：**

```json
{
  "objectKey": "xiaozhi/parent/feedback/202606/123/....jpg",
  "imageUrl": "https://....jpg"
}
```

> `imageUrl` 与统一上传的 `accessUrl` 含义相同。

---

### 3.3 保存头像（上传之后调）

```
PUT /parent-api/auth/profile
Content-Type: application/json
```

**请求 body：**

```json
{
  "nickname": "张三",
  "avatarUrl": "xiaozhi/parent/avatar/202606/123/....jpg"
}
```

| 字段 | 说明 |
|------|------|
| `avatarUrl` | 上传接口返回的 **`objectKey`（推荐）** 或 `accessUrl` / `avatarUrl` |
| 传空字符串 `""` | 清空头像 |

服务端会校验：必须是**当前登录家长**、**avatar 类别**的文件，否则 **20017**。

---

### 3.4 读取头像（我的页 / 编辑页）

```
GET /parent-api/auth/info
```

**响应 data 片段：**

```json
{
  "id": 123,
  "nickname": "张三",
  "avatarUrl": "https://your-bucket.oss-cn-hangzhou.aliyuncs.com/xiaozhi/parent/avatar/....jpg"
}
```

- 库中存的是 `objectKey` 时，**接口已自动 resolve 为可展示的 HTTPS URL**，小程序 `<image src="{{user.avatarUrl}}">` 即可。
- **不要**在客户端对历史数据自行拼路径；以接口返回为准。

---

### 3.5 提交反馈截图（上传之后调）

```
POST /parent-api/feedback
Content-Type: application/json
```

**请求 body 片段：**

```json
{
  "category": "skill",
  "description": "问题描述",
  "imageUrls": [
    "xiaozhi/parent/feedback/202606/123/aaa.jpg",
    "xiaozhi/parent/feedback/202606/123/bbb.jpg"
  ]
}
```

| 字段 | 说明 |
|------|------|
| `imageUrls` | 每项为上传返回的 **`objectKey`（推荐）** 或 `imageUrl`/`accessUrl` |
| 数量 | 最多 **3** 张 |

每张图会校验归属与类别，非法引用 **20017**。

**列表/详情** 返回的 `imageUrls` 已是 resolve 后的 HTTPS 地址，可直接用于 `<image>`。

---

### 3.6 按 key 读取（一般不需要，备用）

私有 Bucket 或特殊场景可用：

```
GET /parent-api/storage/access?key={objectKey}&category=avatar
```

- 需登录；校验 key 属于当前家长。
- OSS 模式：**302 跳转**到 OSS（或签名 URL）。
- 本地模式：直接返回图片字节流。

正常情况用 **3.4 / 3.5 查询接口** 或上传时的 `accessUrl` 即可，**不必**小程序再调此接口。

---

### 3.7 历史本地文件（兼容）

以下接口仍可用，供**改造前已上传**的本地图片展示（匿名 GET）：

```
GET /parent-api/auth/avatar/file/{filename}
GET /parent-api/feedback/image/file/{filename}
```

新上传走 OSS 后，一般**不会**再返回这类 URL。

---

## 四、小程序改造清单

### 4.1 建议新增封装

```
utils/api/storage.js
  - uploadImage(category, filePath)  → 调 POST /parent-api/storage/upload
```

### 4.2 头像页改造

| 步骤 | 原逻辑 | 新逻辑 |
|------|--------|--------|
| 1 | `wx.uploadFile` → `/auth/avatar` | 同上，或改为 `/storage/upload?category=avatar` |
| 2 | 用返回 URL 更新界面 | 预览用 `accessUrl`（或 `avatarUrl`） |
| 3 | `PUT /profile` 传 URL | **改传 `objectKey`（推荐）** |
| 4 | 我的页展示 | 仍用 `GET /info` 的 `avatarUrl`，无需改展示逻辑 |

### 4.3 反馈提交页改造

| 步骤 | 原逻辑 | 新逻辑 |
|------|--------|--------|
| 1 | `wx.uploadFile` → `/feedback/image` | 可保留，或改为 `/storage/upload?category=feedback` |
| 2 | 收集 `imageUrl` | 同时保存 `objectKey`；提交时 **imageUrls 传 objectKey** |
| 3 | 列表/详情展示 | 仍用接口返回的 `imageUrls`，无需改 |

### 4.4 微信小程序后台配置（OSS 开启后必做）

上传仍走 **API 域名**，一般不用改 `uploadFile` 合法域名。

**展示 OSS 图片**时，须把 OSS 访问域名加入小程序后台：

**开发 → 开发管理 → 开发设置 → 服务器域名 → downloadFile 合法域名**

添加例如：

```
https://your-bucket.oss-cn-hangzhou.aliyuncs.com
```

若配置了 CDN，则添加 CDN 域名，例如：

```
https://static.yourdomain.com
```

未配置则 `<image src="https://bucket.oss-...">` 可能无法显示。

---

## 五、代码示例

### 5.1 封装上传（推荐）

```javascript
// utils/api/storage.js
const { BASE_URL, getToken } = require('../config');

function uploadImage(category, filePath) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${BASE_URL}/parent-api/storage/upload`,
      filePath,
      name: 'file',
      formData: { category }, // avatar | feedback
      header: { Authorization: `Bearer ${getToken()}` },
      success(res) {
        const body = JSON.parse(res.data || '{}');
        if (body.code === 0 && body.data) {
          resolve(body.data); // { category, objectKey, accessUrl, oss }
        } else {
          reject(new Error(body.msg || '上传失败'));
        }
      },
      fail: reject,
    });
  });
}

module.exports = { uploadImage };
```

### 5.2 上传头像并保存

```javascript
const { uploadImage } = require('../../utils/api/storage');
const api = require('../../utils/api');

async function onChooseAvatar(tempPath) {
  const uploaded = await uploadImage('avatar', tempPath);
  // 预览
  this.setData({ avatarPreview: uploaded.accessUrl });
  // 保存（推荐 objectKey）
  await api.put('/parent-api/auth/profile', {
    avatarUrl: uploaded.objectKey,
  });
  // 刷新我的页
  const info = await api.get('/parent-api/auth/info');
  this.setData({ avatarUrl: info.data.avatarUrl });
}
```

### 5.3 反馈截图上传并提交

```javascript
const { uploadImage } = require('../../utils/api/storage');

async function uploadFeedbackImages(paths) {
  const keys = [];
  const previews = [];
  for (const p of paths) {
    const r = await uploadImage('feedback', p);
    keys.push(r.objectKey);
    previews.push(r.accessUrl);
  }
  return { keys, previews };
}

// 提交
await api.post('/parent-api/feedback', {
  category: 'skill',
  description: '...',
  imageUrls: keys, // objectKey 数组
});
```

### 5.4 仍用旧接口（最小改动）

仅改 **提交时优先传 objectKey**；上传代码可不动：

```javascript
wx.uploadFile({
  url: `${BASE_URL}/parent-api/feedback/image`,
  filePath,
  name: 'file',
  header: { Authorization: `Bearer ${token}` },
  success(res) {
    const body = JSON.parse(res.data);
    if (body.code === 0) {
      // 新字段 objectKey；imageUrl 仍可用于预览
      const key = body.data.objectKey;
      const preview = body.data.imageUrl;
      imageKeys.push(key);
      previewUrls.push(preview);
    }
  },
});
```

---

## 六、错误码汇总

| code | 含义 | 小程序处理 |
|------|------|------------|
| 20001 | token 无效 | 跳转登录 |
| 20008 | 非内测/反馈未开 | 反馈上传/提交失败提示 |
| 20016 | category 无效 | 检查 formData.category |
| 20017 | 文件引用无效或无权 | 提示重新上传，勿手改 objectKey |
| 10021 | 文件为空 | 提示重新选择 |
| 10022/10024 | 上传失败 | 重试 |

---

## 七、联调检查清单

- [ ] OSS 开启后，上传返回 `oss: true`，`accessUrl` 浏览器可打开
- [ ] 小程序后台已配置 OSS（或 CDN）**downloadFile 合法域名**
- [ ] 头像：`objectKey` 写入 profile 后，`GET /info` 头像正常显示
- [ ] 反馈：提交 1～3 张图，列表/详情缩略图正常
- [ ] 传他人 objectKey 提交 profile/feedback → 应失败 20017
- [ ] OSS 关闭时 `oss: false`，旧本地 URL 逻辑仍可用
- [ ] 历史仅 `filename.jpg` 的旧数据，展示仍正常（后端兼容）

---

## 八、相关文档

| 文档 | 说明 |
|------|------|
| `docs/家长端-内测反馈-小程序改造说明.md` | 反馈业务完整流程（本节仅改上传/URL 部分） |
| 阿里云 OSS 参数字典 | `aliyun.oss.enabled`、`endpoint`、`bucket`、`access_key_*` 等 |

**与内测反馈文档的差异（上传相关）：**

- 上传响应增加 `objectKey`；**提交时推荐传 objectKey**
- `imageUrl` / `avatarUrl` 在开启 OSS 后为 OSS 地址，不再是 `.../parent-api/.../file/...` 形式
- 新增统一入口 `POST /parent-api/storage/upload`
