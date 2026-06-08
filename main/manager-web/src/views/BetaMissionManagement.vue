<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">{{ $t('betaMission.pageTitle') }}</h2>
    </div>
    <div class="main-wrapper">
      <el-tabs v-model="activeTab" @tab-click="onTabChange">
        <el-tab-pane :label="$t('betaMission.tab.config')" name="config">
          <el-card shadow="never" class="cr-card">
            <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
              <span slot="title">{{ $t('betaMission.hintTitle') }}</span>
              <div>{{ $t('betaMission.hintBody') }}</div>
            </el-alert>
            <el-form :model="configForm" label-width="160px" style="max-width: 520px" v-loading="configLoading">
              <el-form-item :label="$t('betaMission.config.enabled')">
                <el-switch v-model="configForm.enabled" />
              </el-form-item>
              <el-form-item :label="$t('betaMission.config.campaign')">
                <span>{{ configMeta.campaignTitle }} ({{ configMeta.campaignCode }})</span>
              </el-form-item>
              <el-form-item :label="$t('betaMission.config.stepCount')">
                <span>{{ configMeta.stepCount }} / {{ $t('betaMission.config.required') }} {{ configMeta.requiredCount }}</span>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="configSaving" @click="saveConfig">
                  {{ $t('betaMission.save') }}
                </el-button>
                <el-button @click="loadConfig" :loading="configLoading">{{ $t('betaMission.reload') }}</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>

        <el-tab-pane :label="$t('betaMission.tab.funnel')" name="funnel">
          <el-card shadow="never" class="cr-card" v-loading="funnelLoading">
            <div class="funnel-summary">
              <div class="stat-item">
                <div class="stat-label">{{ $t('betaMission.funnel.betaTesterTotal') }}</div>
                <div class="stat-value">{{ funnel.betaTesterTotal || 0 }}</div>
              </div>
              <div class="stat-item">
                <div class="stat-label">{{ $t('betaMission.funnel.packCompletedTotal') }}</div>
                <div class="stat-value">{{ funnel.packCompletedTotal || 0 }}</div>
              </div>
              <div class="stat-item" v-if="funnel.betaTesterTotal">
                <div class="stat-label">{{ $t('betaMission.funnel.packRate') }}</div>
                <div class="stat-value">{{ packRate }}%</div>
              </div>
            </div>
            <el-table :data="funnel.steps || []" border style="width: 100%; margin-top: 16px">
              <el-table-column prop="stepKey" :label="$t('betaMission.col.stepKey')" width="180" />
              <el-table-column prop="title" :label="$t('betaMission.col.title')" min-width="160" />
              <el-table-column prop="required" :label="$t('betaMission.col.required')" width="88" align="center">
                <template slot-scope="scope">
                  {{ scope.row.required ? $t('betaMission.yes') : $t('betaMission.no') }}
                </template>
              </el-table-column>
              <el-table-column prop="completedCount" :label="$t('betaMission.col.completed')" width="96" align="center" />
              <el-table-column prop="skippedCount" :label="$t('betaMission.col.skipped')" width="96" align="center" />
              <el-table-column :label="$t('betaMission.col.rate')" width="120" align="center">
                <template slot-scope="scope">
                  {{ formatRate(scope.row.completionRate) }}
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>

        <el-tab-pane :label="$t('betaMission.tab.users')" name="users">
          <el-card shadow="never" class="cr-card">
            <el-form :inline="true" size="small" class="filter-form">
              <el-form-item :label="$t('betaMission.filter.packCompleted')">
                <el-select v-model="userFilters.packCompleted" clearable style="width: 120px">
                  <el-option :label="$t('betaMission.filter.all')" value="" />
                  <el-option :label="$t('betaMission.filter.incomplete')" value="0" />
                  <el-option :label="$t('betaMission.filter.completed')" value="1" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="loadUsers(1)">{{ $t('betaMission.search') }}</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="userList" border v-loading="usersLoading" style="width: 100%">
              <el-table-column prop="parentUserId" :label="$t('betaMission.col.parentId')" width="88" />
              <el-table-column prop="parentNickname" :label="$t('betaMission.col.nickname')" width="120" show-overflow-tooltip />
              <el-table-column prop="requiredDone" :label="$t('betaMission.col.progress')" width="100" align="center">
                <template slot-scope="scope">
                  {{ scope.row.requiredDone }}/{{ scope.row.requiredTotal }}
                </template>
              </el-table-column>
              <el-table-column prop="packCompleted" :label="$t('betaMission.col.packCompleted')" width="100" align="center">
                <template slot-scope="scope">
                  <el-tag :type="scope.row.packCompleted ? 'success' : 'info'" size="small">
                    {{ scope.row.packCompleted ? $t('betaMission.filter.completed') : $t('betaMission.filter.incomplete') }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="contextChildId" :label="$t('betaMission.col.contextChild')" width="110" />
              <el-table-column prop="updateTime" :label="$t('betaMission.col.updateTime')" width="172">
                <template slot-scope="scope">{{ formatTime(scope.row.updateTime) }}</template>
              </el-table-column>
              <el-table-column :label="$t('betaMission.operation')" width="100" fixed="right">
                <template slot-scope="scope">
                  <el-button type="text" size="small" @click="openUserDetail(scope.row.parentUserId)">
                    {{ $t('betaMission.detail') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              style="margin-top: 16px; text-align: right"
              layout="total, prev, pager, next"
              :total="userTotal"
              :page-size="userPageSize"
              :current-page.sync="userPage"
              @current-change="loadUsers"
            />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog :title="$t('betaMission.detailTitle')" :visible.sync="detailVisible" width="720px">
      <div v-loading="detailLoading" v-if="userDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="$t('betaMission.col.parentId')">{{ userDetail.parentUserId }}</el-descriptions-item>
          <el-descriptions-item :label="$t('betaMission.col.nickname')">{{ userDetail.parentNickname || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('betaMission.col.contextChild')">
            {{ userDetail.contextChildName || userDetail.contextChildId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('betaMission.col.packCompleted')">
            {{ userDetail.packCompleted ? $t('betaMission.filter.completed') : $t('betaMission.filter.incomplete') }}
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="userDetail.steps || []" border size="small" style="margin-top: 16px">
          <el-table-column prop="stepKey" :label="$t('betaMission.col.stepKey')" width="180" />
          <el-table-column prop="title" :label="$t('betaMission.col.title')" min-width="140" />
          <el-table-column prop="required" :label="$t('betaMission.col.required')" width="72" align="center">
            <template slot-scope="scope">{{ scope.row.required ? $t('betaMission.yes') : $t('betaMission.no') }}</template>
          </el-table-column>
          <el-table-column prop="status" :label="$t('betaMission.col.status')" width="100" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import HeaderBar from '@/components/HeaderBar.vue'
import Api from '@/apis/api'

export default {
  name: 'BetaMissionManagement',
  components: { HeaderBar },
  data() {
    return {
      activeTab: 'config',
      configLoading: false,
      configSaving: false,
      configForm: { enabled: false },
      configMeta: { campaignCode: '', campaignTitle: '', stepCount: 8, requiredCount: 4 },
      funnelLoading: false,
      funnel: { betaTesterTotal: 0, packCompletedTotal: 0, steps: [] },
      usersLoading: false,
      userList: [],
      userTotal: 0,
      userPage: 1,
      userPageSize: 20,
      userFilters: { packCompleted: '' },
      detailVisible: false,
      detailLoading: false,
      detailUserId: null,
      userDetail: null
    }
  },
  computed: {
    packRate() {
      const t = this.funnel.betaTesterTotal || 0
      const c = this.funnel.packCompletedTotal || 0
      if (!t) return 0
      return Math.round((c * 10000) / t) / 100
    }
  },
  mounted() {
    this.loadConfig()
  },
  methods: {
    onTabChange(tab) {
      if (tab.name === 'funnel') this.loadFunnel()
      if (tab.name === 'users') this.loadUsers(1)
    },
    loadConfig() {
      this.configLoading = true
      Api.admin.getBetaMissionConfig(({ data }) => {
        this.configLoading = false
        if (data && data.code === 0 && data.data) {
          this.configForm.enabled = !!data.data.enabled
          this.configMeta = {
            campaignCode: data.data.campaignCode,
            campaignTitle: data.data.campaignTitle,
            stepCount: data.data.stepCount,
            requiredCount: data.data.requiredCount
          }
        }
      })
    },
    saveConfig() {
      this.configSaving = true
      Api.admin.saveBetaMissionConfig({ enabled: this.configForm.enabled }, ({ data }) => {
        this.configSaving = false
        if (data && data.code === 0) {
          this.$message.success(this.$t('betaMission.saveOk'))
        }
      })
    },
    loadFunnel() {
      this.funnelLoading = true
      Api.admin.getBetaMissionFunnel(({ data }) => {
        this.funnelLoading = false
        if (data && data.code === 0 && data.data) {
          this.funnel = data.data
        }
      })
    },
    loadUsers(page) {
      this.userPage = page || this.userPage
      this.usersLoading = true
      const params = { page: this.userPage, limit: this.userPageSize }
      if (this.userFilters.packCompleted !== '') {
        params.packCompleted = this.userFilters.packCompleted
      }
      Api.admin.getBetaMissionUsers(params, ({ data }) => {
        this.usersLoading = false
        if (data && data.code === 0 && data.data) {
          this.userList = data.data.list || []
          this.userTotal = data.data.total || 0
        }
      })
    },
    openUserDetail(parentUserId) {
      this.detailUserId = parentUserId
      this.detailVisible = true
      this.detailLoading = true
      this.userDetail = null
      Api.admin.getBetaMissionUserDetail(parentUserId, ({ data }) => {
        this.detailLoading = false
        if (data && data.code === 0) {
          this.userDetail = data.data
        }
      })
    },
    formatTime(t) {
      if (!t) return '-'
      return String(t).replace('T', ' ').substring(0, 19)
    },
    formatRate(r) {
      if (r == null) return '-'
      return (r * 100).toFixed(1) + '%'
    }
  }
}
</script>

<style scoped>
.cr-card {
  margin-bottom: 16px;
}
.filter-form {
  margin-bottom: 12px;
}
.funnel-summary {
  display: flex;
  gap: 48px;
  flex-wrap: wrap;
}
.stat-item {
  min-width: 120px;
}
.stat-label {
  color: #909399;
  font-size: 13px;
  margin-bottom: 4px;
}
.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
</style>
