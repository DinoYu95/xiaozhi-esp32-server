<template>
  <el-dialog
    :title="dialogTitle"
    :visible.sync="dialogVisible"
    width="480px"
    @open="handleOpen"
    @close="handleClose">
    <div v-if="agent" class="bind-parent-tip">
      {{ $t('home.bindParentAgent') }}：<strong>{{ agent.agentName }}</strong>
      <span v-if="agent.deviceCount > 0">（{{ $t('home.deviceManagement') }} {{ agent.deviceCount }}）</span>
    </div>
    <div v-if="agent && isUpdate" class="bind-parent-current">
      {{ $t('home.currentBoundParent') }}：<strong>{{ agent.ownerParentNickname || '--' }}</strong>
      <span v-if="agent.ownerParentId">（ID: {{ agent.ownerParentId }}）</span>
    </div>
    <el-form label-width="90px" @submit.native.prevent>
      <el-form-item :label="$t('home.searchParent')">
        <el-input
          v-model="keyword"
          :placeholder="$t('home.searchParentPlaceholder')"
          clearable
          @keyup.enter.native="searchParents">
          <el-button slot="append" icon="el-icon-search" @click="searchParents" />
        </el-input>
      </el-form-item>
      <el-form-item :label="$t('home.selectParent')">
        <el-select
          v-model="selectedParentId"
          filterable
          remote
          :remote-method="searchParents"
          :loading="loading"
          :placeholder="$t('home.selectParentPlaceholder')"
          style="width: 100%">
          <el-option
            v-for="item in parentOptions"
            :key="item.id"
            :label="formatParentLabel(item)"
            :value="item.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <span slot="footer">
      <el-button @click="dialogVisible = false">{{ $t('button.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="confirmBind">{{ $t('button.ok') }}</el-button>
    </span>
  </el-dialog>
</template>

<script>
import Api from '@/apis/api';

export default {
  name: 'BindParentDialog',
  props: {
    visible: { type: Boolean, default: false },
    agent: { type: Object, default: null }
  },
  data() {
    return {
      keyword: '',
      selectedParentId: null,
      parentOptions: [],
      loading: false,
      submitting: false
    };
  },
  computed: {
    dialogVisible: {
      get() { return this.visible; },
      set(val) { this.$emit('update:visible', val); }
    },
    isUpdate() {
      return Boolean(this.agent && this.agent.parentActivated);
    },
    dialogTitle() {
      return this.isUpdate ? this.$t('home.updateParentTitle') : this.$t('home.bindParentTitle');
    }
  },
  methods: {
    handleOpen() {
      this.keyword = '';
      this.selectedParentId = null;
      this.parentOptions = [];
      this.searchParents('');
    },
    handleClose() {
      this.keyword = '';
      this.selectedParentId = null;
      this.parentOptions = [];
    },
    formatParentLabel(item) {
      return `${item.nickname || '家长'}（ID: ${item.id}）`;
    },
    searchParents(query) {
      const kw = typeof query === 'string' ? query : this.keyword;
      this.loading = true;
      Api.agent.searchParentUsers(kw || '', ({ data }) => {
        this.parentOptions = data?.data || [];
        this.loading = false;
      }, () => {
        this.loading = false;
      });
    },
    confirmBind() {
      if (!this.agent || !this.agent.agentId) {
        return;
      }
      if (!this.selectedParentId) {
        this.$message.warning(this.$t('home.selectParentRequired'));
        return;
      }
      const doBind = () => {
        this.submitting = true;
        const payload = {
          parentUserId: this.selectedParentId,
          replaceExisting: this.isUpdate
        };
        Api.agent.bindParent(this.agent.agentId, payload, (res) => {
          this.submitting = false;
          if (res.data?.code === 0) {
            this.$message.success(this.isUpdate ? this.$t('home.updateParentSuccess') : this.$t('home.bindParentSuccess'));
            this.dialogVisible = false;
            this.$emit('success');
          } else {
            this.$message.error(res.data?.msg || (this.isUpdate ? this.$t('home.updateParentFailed') : this.$t('home.bindParentFailed')));
          }
        }, () => {
          this.submitting = false;
          this.$message.error(this.isUpdate ? this.$t('home.updateParentFailed') : this.$t('home.bindParentFailed'));
        });
      };
      if (this.isUpdate) {
        this.$confirm(this.$t('home.updateParentConfirm'), this.$t('home.updateParentTitle'), {
          confirmButtonText: this.$t('button.ok'),
          cancelButtonText: this.$t('button.cancel'),
          type: 'warning'
        }).then(doBind).catch(() => {});
        return;
      }
      doBind();
    }
  }
};
</script>

<style scoped>
.bind-parent-tip {
  margin-bottom: 16px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}

.bind-parent-current {
  margin-bottom: 12px;
  padding: 10px 12px;
  background: #f0f9eb;
  border: 1px solid #c2e7b0;
  border-radius: 8px;
  font-size: 13px;
  color: #529b2e;
}
</style>
