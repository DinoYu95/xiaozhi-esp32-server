<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">{{ $t('parentUser.pageTitle') }}</h2>
    </div>
    <div class="main-wrapper">
      <el-card shadow="never" class="cr-card">
        <el-form :inline="true" size="small" class="filter-form">
          <el-form-item :label="$t('parentUser.filter.keyword')">
            <el-input
              v-model="filters.keyword"
              clearable
              style="width: 220px"
              :placeholder="$t('parentUser.filter.keywordPlaceholder')"
              @keyup.enter.native="loadList(1)"
            />
          </el-form-item>
          <el-form-item :label="$t('parentUser.filter.betaTester')">
            <el-select v-model="filters.betaTester" clearable style="width: 120px">
              <el-option :label="$t('parentUser.filter.all')" value="" />
              <el-option :label="$t('parentUser.yes')" value="1" />
              <el-option :label="$t('parentUser.no')" value="0" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadList(1)">{{ $t('parentUser.search') }}</el-button>
            <el-button @click="resetFilters">{{ $t('parentUser.reset') }}</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="list" border v-loading="loading" style="width: 100%">
          <el-table-column :label="$t('parentUser.col.avatar')" width="72" align="center">
            <template slot-scope="scope">
              <el-avatar :size="40" :src="scope.row.avatarUrl || defaultAvatar" />
            </template>
          </el-table-column>
          <el-table-column prop="id" :label="$t('parentUser.col.id')" width="88" />
          <el-table-column prop="displayNickname" :label="$t('parentUser.col.nickname')" min-width="120" show-overflow-tooltip />
          <el-table-column prop="phoneMasked" :label="$t('parentUser.col.phone')" width="130">
            <template slot-scope="scope">{{ scope.row.phoneMasked || '-' }}</template>
          </el-table-column>
          <el-table-column prop="loginMethods" :label="$t('parentUser.col.loginMethods')" min-width="160" show-overflow-tooltip />
          <el-table-column prop="deviceCount" :label="$t('parentUser.col.deviceCount')" width="96" align="center" />
          <el-table-column prop="betaTester" :label="$t('parentUser.col.betaTester')" width="96" align="center">
            <template slot-scope="scope">
              <el-tag v-if="scope.row.betaTester" type="warning" size="small">{{ $t('parentUser.yes') }}</el-tag>
              <span v-else>{{ $t('parentUser.no') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" :label="$t('parentUser.col.createTime')" width="172">
            <template slot-scope="scope">{{ formatTime(scope.row.createTime) }}</template>
          </el-table-column>
          <el-table-column :label="$t('parentUser.operation')" width="100" fixed="right">
            <template slot-scope="scope">
              <el-button type="text" size="small" @click="openDetail(scope.row.id)">{{ $t('parentUser.detail') }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          style="margin-top: 16px; text-align: right"
          layout="total, prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page.sync="page"
          @current-change="loadList"
        />
      </el-card>
    </div>

    <el-dialog :title="$t('parentUser.detailTitle')" :visible.sync="detailVisible" width="760px" @open="onDetailOpen">
      <div v-loading="detailLoading" v-if="detail">
        <div class="detail-head">
          <el-avatar :size="64" :src="detail.avatarUrl || defaultAvatar" />
          <div class="detail-head-text">
            <div class="detail-name">{{ detail.displayNickname }}</div>
            <div class="detail-sub">ID: {{ detail.id }}</div>
          </div>
        </div>
        <el-descriptions :column="2" border size="small" style="margin-top: 16px">
          <el-descriptions-item :label="$t('parentUser.col.nickname')">{{ detail.nickname || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('parentUser.col.phone')">{{ detail.phoneMasked || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('parentUser.col.betaTester')">
            {{ detail.betaTester ? $t('parentUser.yes') : $t('parentUser.no') }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('parentUser.col.createTime')">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('parentUser.col.updateTime')">{{ formatTime(detail.updateTime) }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-block">
          <div class="section-title">{{ $t('parentUser.section.auths') }}</div>
          <el-table :data="detail.auths || []" border size="small">
            <el-table-column prop="authType" :label="$t('parentUser.col.authType')" width="100" />
            <el-table-column prop="channel" :label="$t('parentUser.col.channel')" width="120" />
            <el-table-column prop="openIdMasked" :label="$t('parentUser.col.openId')" min-width="140">
              <template slot-scope="scope">{{ scope.row.openIdMasked || '-' }}</template>
            </el-table-column>
            <el-table-column prop="phoneMasked" :label="$t('parentUser.col.phone')" width="130">
              <template slot-scope="scope">{{ scope.row.phoneMasked || '-' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" :label="$t('parentUser.col.createTime')" width="172">
              <template slot-scope="scope">{{ formatTime(scope.row.createTime) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div class="section-block">
          <div class="section-title">{{ $t('parentUser.section.devices') }}</div>
          <el-table :data="detail.devices || []" border size="small">
            <el-table-column prop="deviceId" :label="$t('parentUser.col.deviceId')" min-width="180" show-overflow-tooltip />
            <el-table-column prop="role" :label="$t('parentUser.col.role')" width="100" />
            <el-table-column prop="status" :label="$t('parentUser.col.status')" width="100" />
            <el-table-column prop="bindTime" :label="$t('parentUser.col.bindTime')" width="172">
              <template slot-scope="scope">{{ formatTime(scope.row.bindTime) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import HeaderBar from '@/components/HeaderBar.vue'
import Api from '@/apis/api'
import defaultAvatar from '@/assets/home/avatar.png'

export default {
  name: 'ParentUserManagement',
  components: { HeaderBar },
  data() {
    return {
      defaultAvatar,
      loading: false,
      list: [],
      page: 1,
      pageSize: 20,
      total: 0,
      filters: {
        keyword: '',
        betaTester: ''
      },
      detailVisible: false,
      detailLoading: false,
      detailId: null,
      detail: null
    }
  },
  mounted() {
    this.loadList(1)
  },
  methods: {
    loadList(page) {
      if (page) this.page = page
      this.loading = true
      Api.admin.getParentUserPage({
        page: this.page,
        limit: this.pageSize,
        keyword: this.filters.keyword,
        betaTester: this.filters.betaTester
      }, ({ data }) => {
        this.loading = false
        if (data && data.data) {
          this.list = data.data.list || []
          this.total = data.data.total || 0
        }
      })
    },
    resetFilters() {
      this.filters.keyword = ''
      this.filters.betaTester = ''
      this.loadList(1)
    },
    openDetail(id) {
      this.detailId = id
      this.detailVisible = true
    },
    onDetailOpen() {
      if (!this.detailId) return
      this.detailLoading = true
      this.detail = null
      Api.admin.getParentUserDetail(this.detailId, ({ data }) => {
        this.detailLoading = false
        if (data && data.data) {
          this.detail = data.data
        }
      })
    },
    formatTime(value) {
      if (!value) return '-'
      if (typeof value === 'string') return value.replace('T', ' ').substring(0, 19)
      return value
    }
  }
}
</script>

<style scoped>
.main-wrapper {
  padding: 0 24px 24px;
}
.cr-card {
  border-radius: 8px;
}
.filter-form {
  margin-bottom: 8px;
}
.detail-head {
  display: flex;
  align-items: center;
  gap: 16px;
}
.detail-name {
  font-size: 18px;
  font-weight: 600;
}
.detail-sub {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.section-block {
  margin-top: 20px;
}
.section-title {
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
