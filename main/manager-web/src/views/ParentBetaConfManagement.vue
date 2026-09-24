<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">{{ $t('parentBetaConf.pageTitle') }}</h2>
    </div>
    <div class="main-wrapper">
      <el-tabs v-model="activeTab" @tab-click="onTabChange">
        <el-tab-pane :label="$t('parentBetaConf.tab.current')" name="current">
          <el-card shadow="never" class="cr-card" v-loading="loading">
            <el-form label-width="140px">
              <el-form-item :label="$t('parentBetaConf.settings.enabled')">
                <el-switch v-model="form.enabled" />
                <span class="hint">{{ $t('parentBetaConf.settings.enabledHint') }}</span>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="savingSettings" @click="saveSettings">
                  {{ $t('parentBetaConf.saveSettings') }}
                </el-button>
              </el-form-item>
            </el-form>
            <el-divider />
            <div class="stat-row">
              <span>{{ $t('parentBetaConf.currentVersion') }}: <b>{{ form.currentVersion || '-' }}</b></span>
              <span>{{ $t('parentBetaConf.agreedCount') }}: {{ form.agreedCurrentCount || 0 }} / {{ form.parentUserTotal || 0 }}</span>
              <span>{{ $t('parentBetaConf.pendingCount') }}: {{ form.pendingCount || 0 }}</span>
            </div>
            <el-form label-width="140px" style="margin-top: 16px">
              <el-form-item :label="$t('parentBetaConf.field.title')">
                <el-input v-model="form.title" />
              </el-form-item>
              <el-form-item :label="$t('parentBetaConf.field.summary')">
                <el-input v-model="form.summary" type="textarea" :rows="2" maxlength="500" show-word-limit />
              </el-form-item>
              <el-form-item :label="$t('parentBetaConf.field.content')">
                <el-input v-model="form.content" type="textarea" :rows="16" />
              </el-form-item>
              <el-form-item>
                <el-button type="warning" :loading="publishing" @click="publish">
                  {{ $t('parentBetaConf.publish') }}
                </el-button>
                <el-button @click="loadOverview" :loading="loading">{{ $t('parentBetaConf.reload') }}</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>

        <el-tab-pane :label="$t('parentBetaConf.tab.history')" name="history">
          <el-card shadow="never" class="cr-card" v-loading="historyLoading">
            <el-table :data="history" border>
              <el-table-column prop="version" :label="$t('parentBetaConf.col.version')" width="140" />
              <el-table-column prop="title" :label="$t('parentBetaConf.field.title')" min-width="200" />
              <el-table-column prop="status" :label="$t('parentBetaConf.col.status')" width="100" />
              <el-table-column prop="publishedAt" :label="$t('parentBetaConf.col.publishedAt')" width="180" />
            </el-table>
          </el-card>
        </el-tab-pane>

        <el-tab-pane :label="$t('parentBetaConf.tab.pending')" name="pending">
          <el-card shadow="never" class="cr-card" v-loading="pendingLoading">
            <div style="margin-bottom: 12px">
              <el-button size="small" :loading="pendingLoading" @click="loadPending">
                {{ $t('parentBetaConf.reload') }}
              </el-button>
              <span class="pending-hint">{{ $t('parentBetaConf.pendingRefreshHint') }}</span>
            </div>
            <el-table :data="pendingUsers" border>
              <el-table-column prop="parentUserId" label="ID" width="100" />
              <el-table-column prop="nickname" :label="$t('parentBetaConf.col.nickname')" min-width="160" />
              <el-table-column prop="createTime" :label="$t('parentBetaConf.col.registeredAt')" width="180" />
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
  name: 'ParentBetaConfManagement',
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
        enabled: false,
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
      const name = tab && tab.name ? tab.name : this.activeTab
      if (name === 'history') this.loadHistory()
      if (name === 'pending') this.loadPending()
    },
    loadOverview() {
      this.loading = true
      Api.admin.getParentBetaConfOverview(({ data }) => {
        this.loading = false
        if (data && data.data) {
          const d = data.data
          this.form.enabled = d.enabled === true
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
      Api.admin.saveParentBetaConfSettings({ enabled: this.form.enabled }, ({ data }) => {
        this.savingSettings = false
        if (data && data.code === 0) {
          this.$message.success(this.$t('parentBetaConf.saveOk'))
        }
      })
    },
    publish() {
      if (!this.form.title || !this.form.summary || !this.form.content) {
        this.$message.warning(this.$t('parentBetaConf.publishRequired'))
        return
      }
      this.$confirm(this.$t('parentBetaConf.publishConfirm'), this.$t('parentBetaConf.publish'), {
        type: 'warning'
      }).then(() => {
        this.publishing = true
        Api.admin.publishParentBetaConf({
          title: this.form.title,
          summary: this.form.summary,
          content: this.form.content
        }, ({ data }) => {
          this.publishing = false
          if (data && data.code === 0) {
            this.$message.success(this.$t('parentBetaConf.publishOk'))
            this.loadOverview()
          }
        })
      }).catch(() => {})
    },
    loadHistory() {
      this.historyLoading = true
      Api.admin.getParentBetaConfHistory(({ data }) => {
        this.historyLoading = false
        if (data && data.data) {
          this.history = data.data
        }
      })
    },
    loadPending() {
      this.pendingLoading = true
      Api.admin.getParentBetaConfPendingUsers({
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
.hint {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
.pending-hint {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
</style>
