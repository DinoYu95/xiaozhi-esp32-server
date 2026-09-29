<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">小程序审核 / 内测登录</h2>
    </div>
    <div class="main-wrapper">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="访问与文案" name="config">
          <el-card shadow="never" class="cr-card" v-loading="loading">
            <el-form label-width="140px" size="small">
              <el-form-item label="accessMode">
                <el-radio-group v-model="form.accessMode">
                  <el-radio label="open">open（登录即可用）</el-radio>
                  <el-radio label="internal_beta">internal_beta（白名单/已绑定等）</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="reviewMode">
                <el-switch v-model="form.reviewMode" />
                <span class="hint">开启后，白名单中「审核测试」openid 登录永远放行</span>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="savingMode" @click="saveMode">保存访问模式</el-button>
              </el-form-item>
              <el-divider />
              <el-form-item label="应用名">
                <el-input v-model="form.appName" style="max-width: 360px" />
              </el-form-item>
              <el-form-item label="Banner 标题">
                <el-input v-model="form.homeBannerTitle" type="textarea" :rows="2" />
              </el-form-item>
              <el-form-item label="Banner 副标题">
                <el-input v-model="form.homeBannerSubtitle" type="textarea" :rows="3" />
              </el-form-item>
              <el-form-item label="登录页说明">
                <el-input v-model="form.loginHint" type="textarea" :rows="2" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="savingCopy" @click="saveCopy">保存公开文案</el-button>
                <el-button @click="loadSettings">刷新</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>
        <el-tab-pane label="登录白名单" name="allowlist">
          <el-card shadow="never" class="cr-card">
            <el-form :inline="true" size="small" class="filter-form">
              <el-form-item label="关键词">
                <el-input v-model="filters.keyword" clearable placeholder="openid / 手机 / 备注" @keyup.enter.native="loadAllowlist(1)" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="loadAllowlist(1)">查询</el-button>
                <el-button @click="openAllowDialog()">新增</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="allowlist" border v-loading="allowLoading">
              <el-table-column prop="wechatOpenid" label="openid" min-width="200" show-overflow-tooltip />
              <el-table-column prop="channel" label="渠道" width="120" />
              <el-table-column prop="source" label="来源" width="110" />
              <el-table-column label="生效" width="72" align="center">
                <template slot-scope="scope">
                  <el-tag v-if="scope.row.enabled" type="success" size="mini">是</el-tag>
                  <span v-else>否</span>
                </template>
              </el-table-column>
              <el-table-column label="审核号" width="80" align="center">
                <template slot-scope="scope">
                  <el-tag v-if="scope.row.reviewerTest" type="warning" size="mini">是</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
              <el-table-column label="操作" width="140" fixed="right">
                <template slot-scope="scope">
                  <el-button type="text" size="small" @click="openAllowDialog(scope.row)">编辑</el-button>
                  <el-button type="text" size="small" @click="removeAllow(scope.row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              style="margin-top: 16px; text-align: right"
              layout="total, prev, pager, next"
              :total="allowTotal"
              :page-size="allowLimit"
              :current-page.sync="allowPage"
              @current-change="loadAllowlist"
            />
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog title="白名单" :visible.sync="allowDialog" width="520px">
      <el-form label-width="100px" size="small">
        <el-form-item label="openid" required>
          <el-input v-model="allowForm.wechatOpenid" :disabled="!!allowForm.id" />
        </el-form-item>
        <el-form-item label="渠道">
          <el-input v-model="allowForm.channel" placeholder="mini_program" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="allowForm.phone" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="allowForm.source" style="width: 100%">
            <el-option label="manual" value="manual" />
            <el-option label="invite" value="invite" />
            <el-option label="device_bind" value="device_bind" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效">
          <el-switch v-model="allowForm.enabled" />
        </el-form-item>
        <el-form-item label="审核测试">
          <el-switch v-model="allowForm.reviewerTest" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="allowForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="allowDialog = false">取消</el-button>
        <el-button type="primary" :loading="allowSaving" @click="saveAllow">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import HeaderBar from '@/components/HeaderBar.vue'
import Api from '@/apis/api'

export default {
  name: 'ParentAppReviewManagement',
  components: { HeaderBar },
  data() {
    return {
      activeTab: 'config',
      loading: false,
      savingMode: false,
      savingCopy: false,
      form: {
        accessMode: 'internal_beta',
        reviewMode: false,
        appName: '',
        homeBannerTitle: '',
        homeBannerSubtitle: '',
        loginHint: '',
      },
      allowLoading: false,
      allowlist: [],
      allowPage: 1,
      allowLimit: 20,
      allowTotal: 0,
      filters: { keyword: '' },
      allowDialog: false,
      allowSaving: false,
      allowForm: {
        id: null,
        wechatOpenid: '',
        channel: 'mini_program',
        phone: '',
        source: 'manual',
        enabled: true,
        reviewerTest: false,
        remark: '',
      },
    }
  },
  mounted() {
    this.loadSettings()
    this.loadAllowlist(1)
  },
  methods: {
    loadSettings() {
      this.loading = true
      Api.admin.getParentAppSettings(({ data }) => {
        this.loading = false
        if (data.code === 0 && data.data) {
          const d = data.data
          this.form.accessMode = d.accessMode || 'internal_beta'
          this.form.reviewMode = !!d.reviewMode
          this.form.appName = d.appName || ''
          this.form.homeBannerTitle = d.homeBannerTitle || ''
          this.form.homeBannerSubtitle = d.homeBannerSubtitle || ''
          this.form.loginHint = d.loginHint || ''
        }
      })
    },
    saveMode() {
      this.savingMode = true
      Api.admin.saveParentAppSettings(
        { accessMode: this.form.accessMode, reviewMode: this.form.reviewMode },
        ({ data }) => {
          this.savingMode = false
          if (data.code === 0) this.$message.success('已保存')
        }
      )
    },
    saveCopy() {
      this.savingCopy = true
      Api.admin.saveParentAppPublicConfig(
        {
          appName: this.form.appName,
          homeBannerTitle: this.form.homeBannerTitle,
          homeBannerSubtitle: this.form.homeBannerSubtitle,
          loginHint: this.form.loginHint,
        },
        ({ data }) => {
          this.savingCopy = false
          if (data.code === 0) this.$message.success('文案已保存')
        }
      )
    },
    loadAllowlist(page) {
      this.allowPage = page || this.allowPage
      this.allowLoading = true
      Api.admin.getParentAppAllowlistPage(
        { page: this.allowPage, limit: this.allowLimit, keyword: this.filters.keyword },
        ({ data }) => {
          this.allowLoading = false
          if (data.code === 0 && data.data) {
            this.allowlist = data.data.list || []
            this.allowTotal = data.data.total || 0
          }
        }
      )
    },
    openAllowDialog(row) {
      if (row) {
        this.allowForm = {
          id: row.id,
          wechatOpenid: row.wechatOpenid,
          channel: row.channel || 'mini_program',
          phone: row.phone || '',
          source: row.source || 'manual',
          enabled: row.enabled !== false,
          reviewerTest: !!row.reviewerTest,
          remark: row.remark || '',
        }
      } else {
        this.allowForm = {
          id: null,
          wechatOpenid: '',
          channel: 'mini_program',
          phone: '',
          source: 'manual',
          enabled: true,
          reviewerTest: false,
          remark: '',
        }
      }
      this.allowDialog = true
    },
    saveAllow() {
      if (!this.allowForm.wechatOpenid) {
        this.$message.warning('请填写 openid')
        return
      }
      this.allowSaving = true
      Api.admin.saveParentAppAllowlist({ ...this.allowForm }, ({ data }) => {
        this.allowSaving = false
        if (data.code === 0) {
          this.$message.success('已保存')
          this.allowDialog = false
          this.loadAllowlist(this.allowPage)
        }
      })
    },
    removeAllow(id) {
      this.$confirm('确认删除该白名单？', '提示', { type: 'warning' }).then(() => {
        Api.admin.deleteParentAppAllowlist(id, ({ data }) => {
          if (data.code === 0) {
            this.$message.success('已删除')
            this.loadAllowlist(this.allowPage)
          }
        })
      }).catch(() => {})
    },
  },
}
</script>

<style scoped>
.hint {
  margin-left: 12px;
  color: #909399;
  font-size: 12px;
}
</style>
