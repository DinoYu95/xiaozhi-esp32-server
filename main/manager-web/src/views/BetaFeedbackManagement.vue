<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">{{ $t('betaFeedback.pageTitle') }}</h2>
    </div>
    <div class="main-wrapper">
      <el-card shadow="never" class="cr-card">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
          <span slot="title">{{ $t('betaFeedback.hintTitle') }}</span>
          <div>{{ $t('betaFeedback.hintBody') }}</div>
        </el-alert>
        <el-form :inline="true" size="small" class="filter-form">
          <el-form-item :label="$t('betaFeedback.filter.status')">
            <el-select v-model="filters.status" clearable style="width: 120px">
              <el-option v-for="s in statusOptions" :key="s.value" :label="$t(s.labelKey)" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('betaFeedback.filter.category')">
            <el-select v-model="filters.category" clearable style="width: 140px">
              <el-option v-for="c in categoryOptions" :key="c.value" :label="$t(c.labelKey)" :value="c.value" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('betaFeedback.filter.blocking')">
            <el-select v-model="filters.blocking" clearable style="width: 100px">
              <el-option :label="$t('betaFeedback.yes')" value="1" />
              <el-option :label="$t('betaFeedback.no')" value="0" />
            </el-select>
          </el-form-item>
          <el-form-item :label="$t('betaFeedback.filter.parentUserId')">
            <el-input v-model="filters.parentUserId" clearable style="width: 120px" />
          </el-form-item>
          <el-form-item :label="$t('betaFeedback.filter.feedbackNo')">
            <el-input v-model="filters.feedbackNo" clearable style="width: 160px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadList(1)">{{ $t('betaFeedback.search') }}</el-button>
            <el-button @click="resetFilters">{{ $t('betaFeedback.reset') }}</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="list" border v-loading="loading" style="width: 100%">
          <el-table-column prop="feedbackNo" :label="$t('betaFeedback.col.no')" width="160" />
          <el-table-column prop="parentUserId" :label="$t('betaFeedback.col.parentId')" width="88" />
          <el-table-column prop="parentNickname" :label="$t('betaFeedback.col.nickname')" width="100" show-overflow-tooltip />
          <el-table-column prop="category" :label="$t('betaFeedback.col.category')" width="120">
            <template slot-scope="scope">{{ categoryLabel(scope.row.category) }}</template>
          </el-table-column>
          <el-table-column prop="blocking" :label="$t('betaFeedback.col.blocking')" width="88" align="center">
            <template slot-scope="scope">
              <el-tag v-if="scope.row.blocking" type="danger" size="small">{{ $t('betaFeedback.yes') }}</el-tag>
              <span v-else>{{ $t('betaFeedback.no') }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="status" :label="$t('betaFeedback.col.status')" width="100">
            <template slot-scope="scope">{{ statusLabel(scope.row.status) }}</template>
          </el-table-column>
          <el-table-column prop="description" :label="$t('betaFeedback.col.desc')" min-width="200" show-overflow-tooltip />
          <el-table-column prop="createTime" :label="$t('betaFeedback.col.time')" width="172">
            <template slot-scope="scope">{{ formatTime(scope.row.createTime) }}</template>
          </el-table-column>
          <el-table-column :label="$t('betaFeedback.operation')" width="100" fixed="right">
            <template slot-scope="scope">
              <el-button type="text" size="small" @click="openDetail(scope.row.id)">{{ $t('betaFeedback.detail') }}</el-button>
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

    <el-dialog :title="$t('betaFeedback.detailTitle')" :visible.sync="detailVisible" width="800px" @open="onDetailOpen">
      <div v-loading="detailLoading" v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="$t('betaFeedback.col.no')">{{ detail.feedbackNo }}</el-descriptions-item>
          <el-descriptions-item :label="$t('betaFeedback.col.status')">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('betaFeedback.col.parentId')">{{ detail.parentUserId }}</el-descriptions-item>
          <el-descriptions-item :label="$t('betaFeedback.col.nickname')">{{ detail.parentNickname || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('betaFeedback.col.category')">{{ categoryLabel(detail.category) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('betaFeedback.col.blocking')">
            {{ detail.blocking ? $t('betaFeedback.yes') : $t('betaFeedback.no') }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('betaFeedback.col.allowContact')" :span="2">
            {{ detail.allowContact ? $t('betaFeedback.yes') : $t('betaFeedback.no') }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('betaFeedback.col.desc')" :span="2">
            <pre class="desc-pre">{{ detail.description }}</pre>
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.imageUrls && detail.imageUrls.length" class="section-block">
          <div class="section-title">{{ $t('betaFeedback.images') }}</div>
          <div class="img-gallery">
            <el-image
              v-for="(u, i) in detail.imageUrls"
              :key="i"
              class="thumb-el"
              :src="u"
              :preview-src-list="detail.imageUrls"
              fit="cover"
            />
          </div>
        </div>
        <div v-if="contextRows.length" class="section-block">
          <div class="section-title">{{ $t('betaFeedback.context') }}</div>
          <el-descriptions :column="2" border size="small" class="ctx-desc">
            <el-descriptions-item
              v-for="row in contextRows"
              :key="row.key"
              :label="row.label"
              :span="row.span || 1"
            >
              <span class="ctx-value">{{ row.value }}</span>
            </el-descriptions-item>
          </el-descriptions>
          <el-collapse v-if="contextExtraKeys.length" class="ctx-collapse">
            <el-collapse-item :title="$t('betaFeedback.contextMore', { n: contextExtraKeys.length })" name="more">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item
                  v-for="row in contextExtraRows"
                  :key="row.key"
                  :label="row.key"
                >
                  <span class="ctx-value">{{ row.value }}</span>
                </el-descriptions-item>
              </el-descriptions>
            </el-collapse-item>
          </el-collapse>
          <el-collapse class="ctx-collapse">
            <el-collapse-item :title="$t('betaFeedback.contextRaw')" name="raw">
              <pre class="ctx-pre">{{ formatJson(detail.contextSnapshot) }}</pre>
            </el-collapse-item>
          </el-collapse>
        </div>
        <el-divider />
        <el-form label-width="100px" size="small">
          <el-form-item :label="$t('betaFeedback.form.status')">
            <el-select v-model="statusForm.status" style="width: 160px">
              <el-option v-for="s in statusOptions" :key="s.value" :label="$t(s.labelKey)" :value="s.value" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="statusForm.status === 'wont_fix'" :label="$t('betaFeedback.form.wontFix')">
            <el-input v-model="statusForm.wontFixReason" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item :label="$t('betaFeedback.form.adminNote')">
            <el-input v-model="statusForm.adminNote" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="statusSaving" @click="saveStatus">{{ $t('betaFeedback.save') }}</el-button>
          </el-form-item>
        </el-form>
        <el-divider>{{ $t('betaFeedback.betaTesterBlock') }}</el-divider>
        <el-form :inline="true" size="small">
          <el-form-item :label="$t('betaFeedback.col.parentId')">
            <el-input v-model="betaForm.parentUserId" style="width: 120px" />
          </el-form-item>
          <el-form-item>
            <el-switch v-model="betaForm.betaTester" />
            <span class="form-tip">{{ $t('betaFeedback.betaTesterTip') }}</span>
          </el-form-item>
          <el-form-item>
            <el-button @click="saveBetaTester">{{ $t('betaFeedback.saveBeta') }}</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import HeaderBar from '@/components/HeaderBar.vue';
import Api from '@/apis/api';

export default {
  name: 'BetaFeedbackManagement',
  components: { HeaderBar },
  data() {
    return {
      loading: false,
      list: [],
      page: 1,
      pageSize: 20,
      total: 0,
      filters: {
        status: '',
        category: '',
        blocking: '',
        parentUserId: '',
        feedbackNo: '',
      },
      detailVisible: false,
      detailLoading: false,
      detailId: null,
      detail: null,
      statusSaving: false,
      statusForm: {
        status: 'pending',
        adminNote: '',
        wontFixReason: '',
      },
      betaForm: {
        parentUserId: '',
        betaTester: true,
      },
      categoryOptions: [
        { value: 'device_bind', labelKey: 'betaFeedback.cat.device_bind' },
        { value: 'child_voiceprint', labelKey: 'betaFeedback.cat.child_voiceprint' },
        { value: 'chat_voice', labelKey: 'betaFeedback.cat.chat_voice' },
        { value: 'skill', labelKey: 'betaFeedback.cat.skill' },
        { value: 'shadow_mission', labelKey: 'betaFeedback.cat.shadow_mission' },
        { value: 'other', labelKey: 'betaFeedback.cat.other' },
      ],
      statusOptions: [
        { value: 'pending', labelKey: 'betaFeedback.st.pending' },
        { value: 'processing', labelKey: 'betaFeedback.st.processing' },
        { value: 'resolved', labelKey: 'betaFeedback.st.resolved' },
        { value: 'wont_fix', labelKey: 'betaFeedback.st.wont_fix' },
      ],
      contextFieldMeta: [
        { key: 'parent_user_id', labelKey: 'betaFeedback.ctx.parent_user_id' },
        { key: 'child_id', labelKey: 'betaFeedback.ctx.child_id' },
        { key: 'device_id', labelKey: 'betaFeedback.ctx.device_id' },
        { key: 'mini_program_version', labelKey: 'betaFeedback.ctx.mini_program_version' },
        { key: 'page_path', labelKey: 'betaFeedback.ctx.page_path', span: 2 },
        { key: 'page_query', labelKey: 'betaFeedback.ctx.page_query', span: 2 },
        { key: 'client_time', labelKey: 'betaFeedback.ctx.client_time', span: 2 },
        { key: 'network_type', labelKey: 'betaFeedback.ctx.network_type' },
        { key: 'system', labelKey: 'betaFeedback.ctx.system' },
        { key: 'model', labelKey: 'betaFeedback.ctx.model' },
      ],
    };
  },
  computed: {
    contextRows() {
      const snap = this.detail && this.detail.contextSnapshot;
      if (!snap || typeof snap !== 'object') return [];
      const known = new Set(this.contextFieldMeta.map((m) => m.key));
      const rows = [];
      for (const meta of this.contextFieldMeta) {
        const v = snap[meta.key];
        if (v === undefined || v === null || v === '') continue;
        rows.push({
          key: meta.key,
          label: this.$t(meta.labelKey),
          value: this.formatContextValue(meta.key, v),
          span: meta.span,
        });
      }
      return rows;
    },
    contextExtraKeys() {
      const snap = this.detail && this.detail.contextSnapshot;
      if (!snap || typeof snap !== 'object') return [];
      const known = new Set(this.contextFieldMeta.map((m) => m.key));
      return Object.keys(snap).filter((k) => !known.has(k));
    },
    contextExtraRows() {
      const snap = this.detail && this.detail.contextSnapshot;
      if (!snap) return [];
      return this.contextExtraKeys.map((k) => ({
        key: k,
        value: this.formatContextValue(k, snap[k]),
      }));
    },
  },
  mounted() {
    this.loadList(1);
  },
  methods: {
    categoryLabel(v) {
      const o = this.categoryOptions.find((x) => x.value === v);
      return o ? this.$t(o.labelKey) : v;
    },
    statusLabel(v) {
      const o = this.statusOptions.find((x) => x.value === v);
      return o ? this.$t(o.labelKey) : v;
    },
    formatTime(val) {
      if (!val) return '-';
      const d = new Date(val);
      return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString('zh-CN');
    },
    formatJson(obj) {
      try {
        return JSON.stringify(obj, null, 2);
      } catch (e) {
        return String(obj);
      }
    },
    formatContextValue(key, val) {
      if (val === null || val === undefined) return '-';
      if (typeof val === 'object') {
        try {
          return JSON.stringify(val);
        } catch (e) {
          return String(val);
        }
      }
      if (key === 'client_time' && typeof val === 'string') {
        const d = new Date(val);
        if (!Number.isNaN(d.getTime())) {
          return `${val}（${d.toLocaleString('zh-CN')}）`;
        }
      }
      return String(val);
    },
    resetFilters() {
      this.filters = {
        status: '',
        category: '',
        blocking: '',
        parentUserId: '',
        feedbackNo: '',
      };
      this.loadList(1);
    },
    loadList(p) {
      this.page = p || this.page;
      this.loading = true;
      const params = {
        page: this.page,
        limit: this.pageSize,
        status: this.filters.status,
        category: this.filters.category,
        blocking: this.filters.blocking,
        parentUserId: this.filters.parentUserId,
        feedbackNo: this.filters.feedbackNo,
      };
      Api.admin.getBetaFeedbackPage(params, ({ data }) => {
        this.loading = false;
        if (data.code === 0 && data.data) {
          this.list = data.data.list || [];
          this.total = data.data.total || 0;
        }
      });
    },
    openDetail(id) {
      this.detailId = id;
      this.detailVisible = true;
    },
    onDetailOpen() {
      if (!this.detailId) return;
      this.detailLoading = true;
      Api.admin.getBetaFeedbackDetail(this.detailId, ({ data }) => {
        this.detailLoading = false;
        if (data.code === 0 && data.data) {
          this.detail = data.data;
          this.statusForm.status = data.data.status || 'pending';
          this.statusForm.adminNote = data.data.adminNote || '';
          this.statusForm.wontFixReason = data.data.wontFixReason || '';
          this.betaForm.parentUserId = String(data.data.parentUserId || '');
          this.betaForm.betaTester = true;
        }
      });
    },
    saveStatus() {
      this.statusSaving = true;
      Api.admin.updateBetaFeedbackStatus(
        this.detailId,
        {
          status: this.statusForm.status,
          adminNote: this.statusForm.adminNote,
          wontFixReason: this.statusForm.wontFixReason,
        },
        ({ data }) => {
          this.statusSaving = false;
          if (data.code === 0) {
            this.$message.success(this.$t('betaFeedback.saveOk'));
            this.onDetailOpen();
            this.loadList(this.page);
          }
        }
      );
    },
    saveBetaTester() {
      const id = parseInt(this.betaForm.parentUserId, 10);
      if (!id) {
        this.$message.warning(this.$t('betaFeedback.parentIdRequired'));
        return;
      }
      Api.admin.setBetaTester(
        { parentUserId: id, betaTester: !!this.betaForm.betaTester },
        ({ data }) => {
          if (data.code === 0) {
            this.$message.success(this.$t('betaFeedback.saveOk'));
          }
        }
      );
    },
  },
};
</script>

<style scoped>
.filter-form {
  margin-bottom: 12px;
}
.desc-pre,
.ctx-pre {
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
  font-family: inherit;
  font-size: 13px;
}
.section-block {
  margin-top: 16px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}
.ctx-desc {
  margin-bottom: 8px;
}
.ctx-value {
  word-break: break-all;
  line-height: 1.5;
}
.ctx-collapse {
  margin-top: 8px;
  border: none;
}
.ctx-collapse >>> .el-collapse-item__header {
  height: 36px;
  line-height: 36px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding-left: 12px;
  border-radius: 4px;
}
.ctx-pre {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  max-height: 200px;
  overflow: auto;
  font-family: Menlo, Monaco, Consolas, monospace;
}
.img-gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.thumb-el {
  width: 100px;
  height: 100px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
}
.form-tip {
  margin-left: 8px;
  color: #909399;
  font-size: 12px;
}
</style>
