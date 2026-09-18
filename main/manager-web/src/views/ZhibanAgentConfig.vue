<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">智伴 Agent 配置</h2>
    </div>
    <div class="main-wrapper">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
        <span slot="title">说明</span>
        <div>
          此处配置 zhiban-agent 运行时参数。API Key 请在「模型配置」中维护，此处通过「模型 ID」引用。
          ECS 上仅需保留 MANAGER_API_BASE、MANAGER_API_SECRET、POSTGRES_MEMORY_URL。
        </div>
      </el-alert>
      <el-tabs v-model="activeTab" v-loading="loading">
        <el-tab-pane label="LLM 策略" name="llm">
          <el-card shadow="never" class="zb-card">
            <div v-for="row in profileRows" :key="row.key" class="profile-block">
              <h4>{{ row.label }} <span class="profile-key">({{ row.key }})</span></h4>
              <p class="profile-desc">{{ row.desc }}</p>
              <el-form label-width="120px" style="max-width: 720px">
                <el-form-item label="模型 ID">
                  <el-select
                    v-model="form.llmProfiles[row.key].llmModelId"
                    clearable
                    filterable
                    placeholder="选择 LLM 模型配置"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="m in llmOptions"
                      :key="m.value"
                      :label="m.label"
                      :value="m.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="temperature">
                  <el-input-number
                    v-model="form.llmProfiles[row.key].temperature"
                    :min="0"
                    :max="2"
                    :step="0.05"
                    :precision="2"
                  />
                </el-form-item>
                <el-form-item v-if="row.key === 'router'" label="max_tokens">
                  <el-input-number v-model="form.llmProfiles[row.key].maxTokens" :min="64" :max="4096" />
                </el-form-item>
              </el-form>
            </div>
            <el-divider />
            <h4>Embedding 向量模型</h4>
            <el-form label-width="120px" style="max-width: 720px">
              <el-form-item label="模型 ID">
                <el-select
                  v-model="form.embedding.llmModelId"
                  clearable
                  filterable
                  placeholder="选择 embedding 用 LLM 配置"
                  style="width: 100%"
                >
                  <el-option v-for="m in llmOptions" :key="m.value" :label="m.label" :value="m.value" />
                </el-select>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="集成" name="integration">
          <el-card shadow="never" class="zb-card">
            <el-form label-width="180px" style="max-width: 640px">
              <el-form-item label="xiaozhi-server URL">
                <el-input v-model="form.integration.xiaozhiServerUrl" placeholder="http://xiaozhi-server:8003" />
              </el-form-item>
              <el-form-item label="MCP 超时（秒）">
                <el-input-number v-model="form.integration.mcpCallTimeoutSec" :min="5" :max="120" />
              </el-form-item>
              <el-form-item label="拍照超时（秒）">
                <el-input-number v-model="form.integration.photoMcpTimeoutSec" :min="5" :max="180" />
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="记忆" name="memory">
          <el-card shadow="never" class="zb-card">
            <el-form label-width="220px" style="max-width: 640px">
              <el-form-item label="短句跳过路由 LLM">
                <el-switch v-model="form.memory.routerFastPath" />
              </el-form-item>
              <el-form-item label="记忆上下文最大字符">
                <el-input-number v-model="form.memory.loadContextMaxChars" :min="200" :max="8000" />
              </el-form-item>
              <el-form-item label="无记录时跳过向量检索">
                <el-switch v-model="form.memory.episodicSkipIfEmpty" />
              </el-form-item>
              <el-form-item label="禁止写入助手身份记忆">
                <el-switch v-model="form.memory.blockAgentIdentity" />
              </el-form-item>
              <el-form-item label="作业记忆 TTL（天）">
                <el-input-number v-model="form.memory.homeworkEpisodicTtlDays" :min="1" :max="365" />
              </el-form-item>
              <el-form-item label="用户画像记忆">
                <el-switch v-model="form.memory.profileEnabled" />
              </el-form-item>
              <el-form-item label="会话摘要记忆">
                <el-switch v-model="form.memory.summaryEnabled" />
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>

        <el-tab-pane label="功能开关" name="features">
          <el-card shadow="never" class="zb-card">
            <el-form label-width="220px" style="max-width: 640px">
              <el-form-item label="作业拍照判断">
                <el-switch v-model="form.features.homeworkPhotoJudgeEnabled" />
              </el-form-item>
              <el-form-item label="儿童风险 LLM 判别">
                <el-switch v-model="form.features.childRiskLlmJudgeEnabled" />
              </el-form-item>
              <el-form-item label="风险 LLM 每 N 轮">
                <el-input-number v-model="form.features.childRiskLlmEveryN" :min="1" :max="99" />
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>
      </el-tabs>
      <div class="footer-actions">
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        <el-button :loading="loading" @click="load">重新加载</el-button>
      </div>
    </div>
  </div>
</template>

<script>
import HeaderBar from '@/components/HeaderBar.vue'
import Api from '@/apis/api'

const PROFILE_KEYS = ['router', 'chat', 'vision', 'extract', 'parentTools', 'childRiskJudge']

function emptyProfile(temp, maxTokens) {
  return { llmModelId: '', temperature: temp, maxTokens: maxTokens || undefined }
}

function defaultForm() {
  const llmProfiles = {}
  llmProfiles.router = emptyProfile(0, 256)
  llmProfiles.chat = emptyProfile(0.45)
  llmProfiles.vision = emptyProfile(0.35)
  llmProfiles.extract = emptyProfile(0)
  llmProfiles.parentTools = emptyProfile(0.35)
  llmProfiles.childRiskJudge = emptyProfile(0)
  return {
    version: 1,
    llmProfiles,
    embedding: { llmModelId: '' },
    integration: {
      xiaozhiServerUrl: 'http://xiaozhi-server:8003',
      mcpCallTimeoutSec: 45,
      photoMcpTimeoutSec: 60,
    },
    memory: {
      routerFastPath: true,
      loadContextMaxChars: 1200,
      episodicSkipIfEmpty: true,
      blockAgentIdentity: true,
      homeworkEpisodicTtlDays: 30,
      profileEnabled: true,
      summaryEnabled: true,
    },
    features: {
      homeworkPhotoJudgeEnabled: true,
      childRiskLlmJudgeEnabled: false,
      childRiskLlmEveryN: 1,
    },
  }
}

function mergeForm(data) {
  const base = defaultForm()
  if (!data) return base
  PROFILE_KEYS.forEach((k) => {
    if (data.llmProfiles && data.llmProfiles[k]) {
      base.llmProfiles[k] = { ...base.llmProfiles[k], ...data.llmProfiles[k] }
    }
  })
  if (data.embedding) base.embedding = { ...base.embedding, ...data.embedding }
  if (data.integration) base.integration = { ...base.integration, ...data.integration }
  if (data.memory) base.memory = { ...base.memory, ...data.memory }
  if (data.features) base.features = { ...base.features, ...data.features }
  if (data.version) base.version = data.version
  return base
}

export default {
  name: 'ZhibanAgentConfig',
  components: { HeaderBar },
  data() {
    return {
      activeTab: 'llm',
      loading: false,
      saving: false,
      llmOptions: [],
      form: defaultForm(),
      profileRows: [
        { key: 'router', label: '意图路由', desc: '寒暄/意图分类，建议小模型 + temperature=0' },
        { key: 'chat', label: '主对话', desc: '设备/家长主聊天回复' },
        { key: 'vision', label: '多模态视觉', desc: '设备拍照返图理解，须 VL 模型' },
        { key: 'extract', label: '记忆抽取', desc: '结构化抽取/摘要，建议小模型 + temp=0' },
        { key: 'parentTools', label: '家长 Tool Loop', desc: '家长端工具调用与澄清' },
        { key: 'childRiskJudge', label: '风险 LLM 判别', desc: '儿童风险 LLM judge（可与 router 同模型）' },
      ],
    }
  },
  mounted() {
    this.loadLlmOptions()
    this.load()
  },
  methods: {
    loadLlmOptions() {
      Api.model.getLlmModelCodeList('', ({ data }) => {
        if (data.code === 0 && data.data) {
          this.llmOptions = data.data.map((item) => ({
            value: item.id,
            label: `${item.modelName} (${item.id})`,
          }))
        }
      })
    },
    load() {
      this.loading = true
      Api.admin.getZhibanAgentConfig(({ data }) => {
        this.loading = false
        if (data.code === 0) {
          this.form = mergeForm(data.data)
        } else {
          this.$message.error(data.msg || '加载失败')
        }
      })
    },
    save() {
      this.saving = true
      Api.admin.saveZhibanAgentConfig(this.form, ({ data }) => {
        this.saving = false
        if (data.code === 0) {
          this.$message.success('已保存；zhiban-agent 约 1 分钟内自动生效')
        } else {
          this.$message.error(data.msg || '保存失败')
        }
      })
    },
  },
}
</script>

<style scoped>
.main-wrapper {
  padding: 16px 24px 80px;
}
.zb-card {
  margin-top: 8px;
}
.profile-block {
  margin-bottom: 24px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #ebeef5;
}
.profile-block:last-of-type {
  border-bottom: none;
}
.profile-key {
  font-size: 12px;
  color: #909399;
  font-weight: normal;
}
.profile-desc {
  font-size: 13px;
  color: #606266;
  margin: 4px 0 12px;
}
.footer-actions {
  margin-top: 16px;
}
</style>
