<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">{{ $t('parentConsent.pageTitle') }}</h2>
    </div>
    <div class="main-wrapper">
      <el-tabs v-model="activeTab" @tab-click="onTabChange">
        <el-tab-pane :label="$t('parentConsent.tab.current')" name="current">
          <el-card shadow="never" class="cr-card" v-loading="loading">
            <el-form label-width="140px">
              <el-form-item :label="$t('parentConsent.settings.enabled')">
                <el-switch v-model="form.enabled" />
              </el-form-item>
              <el-form-item :label="$t('parentConsent.settings.deviceBlockMode')">
                <el-select v-model="form.deviceBlockMode" style="width: 240px">
                  <el-option :label="$t('parentConsent.mode.ownerOnly')" value="owner_only" />
                  <el-option :label="$t('parentConsent.mode.allMembers')" value="all_members" />
                </el-select>
              </el-form-item>
              <el-form-item :label="$t('parentConsent.settings.devicePrompt')">
                <el-input v-model="form.deviceBlockedPrompt" type="textarea" :rows="2" />
              </el-form-item>
              <el-form-item :label="$t('parentConsent.settings.retentionDays')">
                <el-input-number v-model="form.retentionDaysDisplay" :min="1" :max="3650" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="savingSettings" @click="saveSettings">
                  {{ $t('parentConsent.saveSettings') }}
                </el-button>
              </el-form-item>
            </el-form>
            <el-divider />
            <div class="stat-row">
              <span>{{ $t('parentConsent.currentVersion') }}: <b>{{ form.currentVersion || '-' }}</b></span>
              <span>{{ $t('parentConsent.agreedCount') }}: {{ form.agreedCurrentCount || 0 }} / {{ form.parentUserTotal || 0 }}</span>
              <span>{{ $t('parentConsent.pendingCount') }}: {{ form.pendingCount || 0 }}</span>
            </div>
            <el-form label-width="140px" style="margin-top: 16px">
              <el-form-item :label="$t('parentConsent.field.title')">
                <el-input v-model="form.title" />
              </el-form-item>
              <el-form-item :label="$t('parentConsent.field.summary')">
                <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit />
              </el-form-item>
              <el-form-item :label="$t('parentConsent.field.content')">
                <el-input v-model="form.content" type="textarea" :rows="16" />
              </el-form-item>
              <el-form-item>
                <el-button type="warning" :loading="publishing" @click="publish">
                  {{ $t('parentConsent.publish') }}
                </el-button>
                <el-button @click="loadOverview" :loading="loading">{{ $t('parentConsent.reload') }}</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>

        <el-tab-pane :label="$t('parentConsent.tab.history')" name="history">
          <el-card shadow="never" class="cr-card" v-loading="historyLoading">
            <el-table :data="history" border>
              <el-table-column prop="version" :label="$t('parentConsent.col.version')" width="140" />
              <el-table-column prop="title" :label="$t('parentConsent.field.title')" min-width="200" />
              <el-table-column prop="status" :label="$t('parentConsent.col.status')" width="100" />
              <el-table-column prop="publishedAt" :label="$t('parentConsent.col.publishedAt')" width="180" />
            </el-table>
          </el-card>
        </el-tab-pane>

        <el-tab-pane :label="$t('parentConsent.tab.pending')" name="pending">
          <el-card shadow="never" class="cr-card" v-loading="pendingLoading">
            <el-table :data="pendingUsers" border>
              <el-table-column prop="parentUserId" label="ID" width="100" />
              <el-table-column prop="nickname" :label="$t('parentConsent.col.nickname')" min-width="160" />
              <el-table-column prop="createTime" :label="$t('parentConsent.col.registeredAt')" width="180" />
            </el-table>
            <el-pagination
              style="margin-top: 16px"
              layout="total, prev, pager, next"
              :total="pendingTotal"
              :page-size="pendingLimit"
              :current-page.sync="pendingPage"
              @current-change="loadPending"
            />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script>
import HeaderBar from '@/components/HeaderBar.vue'
import Api from '@/apis/api'

export default {
  name: 'ParentConsentManagement',
  components: { HeaderBar },
  data() {
    return {
      activeTab: 'current',
      loading: false,
      savingSettings: false,
      publishing: false,
      historyLoading: false,
      pendingLoading: false,
      form: {
        enabled: true,
        deviceBlockMode: 'owner_only',
        deviceBlockedPrompt: '',
        retentionDaysDisplay: 180,
        currentVersion: '',
        title: '',
        summary: '',
        content: '',
        agreedCurrentCount: 0,
        parentUserTotal: 0,
        pendingCount: 0
      },
      history: [],
      pendingUsers: [],
      pendingPage: 1,
      pendingLimit: 20,
      pendingTotal: 0
    }
  },
  mounted() {
    this.loadOverview()
  },
  methods: {
    onTabChange(tab) {
      if (tab.name === 'history') this.loadHistory()
      if (tab.name === 'pending') this.loadPending()
    },
    loadOverview() {
      this.loading = true
      Api.admin.getParentConsentOverview(({ data }) => {
        this.loading = false
        if (data && data.data) {
          const d = data.data
          this.form.enabled = d.enabled !== false
          this.form.deviceBlockMode = d.deviceBlockMode || 'owner_only'
          this.form.deviceBlockedPrompt = d.deviceBlockedPrompt || ''
          this.form.retentionDaysDisplay = d.retentionDaysDisplay || 180
          this.form.currentVersion = d.currentVersion
          this.form.title = d.title || ''
          this.form.summary = d.summary || ''
          this.form.content = d.content || ''
          this.form.agreedCurrentCount = d.agreedCurrentCount
          this.form.parentUserTotal = d.parentUserTotal
          this.form.pendingCount = d.pendingCount
        }
      })
    },
    saveSettings() {
      this.savingSettings = true
      Api.admin.saveParentConsentSettings({
        enabled: this.form.enabled,
        deviceBlockMode: this.form.deviceBlockMode,
        deviceBlockedPrompt: this.form.deviceBlockedPrompt,
        retentionDaysDisplay: this.form.retentionDaysDisplay
      }, ({ data }) => {
        this.savingSettings = false
        if (data && data.code === 0) {
          this.$message.success(this.$t('parentConsent.saveOk'))
        }
      })
    },
    publish() {
      if (!this.form.title || !this.form.summary || !this.form.content) {
        this.$message.warning(this.$t('parentConsent.publishRequired'))
        return
      }
      this.$confirm(this.$t('parentConsent.publishConfirm'), this.$t('parentConsent.publish'), {
        type: 'warning'
      }).then(() => {
        this.publishing = true
        Api.admin.publishParentConsent({
          title: this.form.title,
          summary: this.form.summary,
          content: this.form.content
        }, ({ data }) => {
          this.publishing = false
          if (data && data.code === 0) {
            this.$message.success(this.$t('parentConsent.publishOk'))
            this.loadOverview()
          }
        })
      }).catch(() => {})
    },
    loadHistory() {
      this.historyLoading = true
      Api.admin.getParentConsentHistory(({ data }) => {
        this.historyLoading = false
        if (data && data.data) {
          this.history = data.data
        }
      })
    },
    loadPending() {
      this.pendingLoading = true
      Api.admin.getParentConsentPendingUsers({
        page: this.pendingPage,
        limit: this.pendingLimit
      }, ({ data }) => {
        this.pendingLoading = false
        if (data && data.data) {
          this.pendingUsers = data.data.list || []
          this.pendingTotal = data.data.total || 0
        }
      })
    }
  }
}
</script>

<style scoped>
.main-wrapper {
  padding: 0 24px 24px;
}
.cr-card {
  margin-top: 8px;
}
.stat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  color: #606266;
  font-size: 14px;
}
</style>
