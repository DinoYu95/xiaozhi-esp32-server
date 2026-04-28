<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">{{ $t('header.childRiskManagement') }}</h2>
    </div>
    <div class="main-wrapper">
      <el-tabs v-model="activeTab" @tab-click="onTabChange">
        <el-tab-pane :label="$t('childRisk.tab.config')" name="config">
          <el-card shadow="never" class="cr-card">
            <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px">
              <span slot="title">{{ $t('childRisk.config.hintTitle') }}</span>
              <div>{{ $t('childRisk.config.hintBody') }}</div>
            </el-alert>
            <el-form ref="cfgForm" :model="configForm" label-width="220px" style="max-width: 640px" v-loading="configLoading">
              <el-form-item :label="$t('childRisk.config.enabled')">
                <el-switch v-model="configForm.enabled" />
              </el-form-item>
              <el-form-item :label="$t('childRisk.config.cooldownMinutes')">
                <el-input-number v-model="configForm.cooldownMinutes" :min="1" :max="10080" />
                <span class="form-tip">{{ $t('childRisk.config.cooldownMinutesTip') }}</span>
              </el-form-item>
              <el-form-item :label="$t('childRisk.config.notifyIfRiskLevelLte')">
                <el-input-number v-model="configForm.notifyIfRiskLevelLte" :min="1" :max="3" />
                <span class="form-tip">{{ $t('childRisk.config.notifyLevelTip') }}</span>
              </el-form-item>
              <el-form-item :label="$t('childRisk.config.evalEveryNRounds')">
                <el-input-number v-model="configForm.evalEveryNRounds" :min="1" :max="99" />
                <span class="form-tip">{{ $t('childRisk.config.evalTip') }}</span>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="configSaving" @click="saveConfig">
                  {{ $t('childRisk.save') }}
                </el-button>
                <el-button @click="loadConfig" :loading="configLoading">{{ $t('childRisk.reload') }}</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-tab-pane>

        <el-tab-pane :label="$t('childRisk.tab.rules')" name="rules">
          <div class="toolbar-row">
            <el-button type="primary" size="small" @click="openRuleDialog(null)">{{ $t('childRisk.rule.add') }}</el-button>
          </div>
          <el-table :data="ruleList" border v-loading="rulesLoading" style="width: 100%">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="name" :label="$t('childRisk.rule.name')" width="140" />
            <el-table-column prop="ruleType" label="类型" width="100" />
            <el-table-column prop="pattern" :label="$t('childRisk.rule.pattern')" min-width="200" show-overflow-tooltip />
            <el-table-column prop="riskLevel" :label="$t('childRisk.rule.riskLevel')" width="96" />
            <el-table-column prop="category" :label="$t('childRisk.rule.category')" width="120" />
            <el-table-column prop="status" :label="$t('childRisk.rule.status')" width="88">
              <template slot-scope="scope">
                <el-tag :type="scope.row.status === 1 ? 'success' : 'info'" size="small">
                  {{ scope.row.status === 1 ? $t('childRisk.rule.enabled') : $t('childRisk.rule.disabled') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('childRisk.operation')" width="160" fixed="right">
              <template slot-scope="scope">
                <el-button type="text" size="small" @click="openRuleDialog(scope.row)">{{ $t('childRisk.edit') }}</el-button>
                <el-button type="text" size="small" class="danger-text" @click="removeRule(scope.row)">{{ $t('childRisk.delete') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="$t('childRisk.tab.events')" name="events">
          <el-table :data="eventList" border v-loading="eventsLoading" style="width: 100%">
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="childId" :label="$t('childRisk.event.childId')" width="88" />
            <el-table-column prop="deviceId" :label="$t('childRisk.event.deviceId')" min-width="140" show-overflow-tooltip />
            <el-table-column prop="riskLevel" :label="$t('childRisk.rule.riskLevel')" width="88" />
            <el-table-column prop="category" :label="$t('childRisk.rule.category')" width="120" />
            <el-table-column prop="source" label="source" width="100" />
            <el-table-column prop="status" :label="$t('childRisk.event.status')" width="120" />
            <el-table-column prop="reasonPublic" :label="$t('childRisk.event.reason')" min-width="160" show-overflow-tooltip />
            <el-table-column prop="createTime" :label="$t('childRisk.event.time')" width="172">
              <template slot-scope="scope">
                {{ formatTime(scope.row.createTime) }}
              </template>
            </el-table-column>
          </el-table>
          <div class="pager-wrap" v-if="eventTotal > 0">
            <el-pagination
              background
              layout="prev, pager, next, total"
              :current-page.sync="eventPage"
              :page-size="eventPageSize"
              :total="eventTotal"
              @current-change="loadEvents"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog :title="ruleForm.id ? $t('childRisk.rule.editTitle') : $t('childRisk.rule.addTitle')" :visible.sync="ruleDialogVisible" width="560px" @closed="resetRuleForm">
      <el-form :model="ruleForm" label-width="112px">
        <el-form-item :label="$t('childRisk.rule.name')" required>
          <el-input v-model="ruleForm.name" maxlength="64" show-word-limit />
        </el-form-item>
        <el-form-item label="ruleType" required>
          <el-select v-model="ruleForm.ruleType" style="width: 100%">
            <el-option label="KEYWORD" value="KEYWORD" />
            <el-option label="REGEX" value="REGEX" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('childRisk.rule.pattern')" required>
          <el-input v-model="ruleForm.pattern" type="textarea" :rows="3" maxlength="512" show-word-limit />
        </el-form-item>
        <el-form-item :label="$t('childRisk.rule.riskLevel')" required>
          <el-input-number v-model="ruleForm.riskLevel" :min="1" :max="3" />
          <span class="form-tip">{{ $t('childRisk.rule.riskLevelTip') }}</span>
        </el-form-item>
        <el-form-item :label="$t('childRisk.rule.category')" required>
          <el-input v-model="ruleForm.category" maxlength="64" />
        </el-form-item>
        <el-form-item :label="$t('childRisk.rule.sortOrder')">
          <el-input-number v-model="ruleForm.sortOrder" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="$t('childRisk.rule.status')">
          <el-radio-group v-model="ruleForm.status">
            <el-radio :label="1">{{ $t('childRisk.rule.enabled') }}</el-radio>
            <el-radio :label="0">{{ $t('childRisk.rule.disabled') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="ruleDialogVisible = false">{{ $t('button.cancel') }}</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="submitRule">{{ $t('button.ok') }}</el-button>
      </span>
    </el-dialog>

    <el-footer>
      <version-footer />
    </el-footer>
  </div>
</template>

<script>
import Api from '@/apis/api';
import HeaderBar from '@/components/HeaderBar.vue';
import VersionFooter from '@/components/VersionFooter.vue';

export default {
  name: 'ChildRiskManagement',
  components: { HeaderBar, VersionFooter },
  data() {
    return {
      activeTab: 'config',
      configLoading: false,
      configSaving: false,
      configForm: {
        enabled: false,
        cooldownMinutes: 30,
        notifyIfRiskLevelLte: 3,
        evalEveryNRounds: 3,
      },
      ruleList: [],
      rulesLoading: false,
      ruleDialogVisible: false,
      ruleSaving: false,
      ruleForm: {
        id: null,
        name: '',
        ruleType: 'KEYWORD',
        pattern: '',
        riskLevel: 2,
        category: 'other',
        sortOrder: 0,
        status: 1,
      },
      eventList: [],
      eventsLoading: false,
      eventPage: 1,
      eventPageSize: 20,
      eventTotal: 0,
    };
  },
  mounted() {
    this.loadConfig();
  },
  methods: {
    formatTime(val) {
      if (!val) return '-';
      const d = new Date(val);
      if (Number.isNaN(d.getTime())) return '-';
      return d.toLocaleString(this.$i18n.locale === 'en' ? 'en-US' : 'zh-CN');
    },
    onTabChange(tab) {
      if (tab.name === 'rules') this.fetchRules();
      if (tab.name === 'events') this.loadEvents(1);
    },
    loadConfig() {
      this.configLoading = true;
      Api.admin.getChildRiskConfig(({ data }) => {
        this.configLoading = false;
        if (data.code === 0 && data.data) {
          const x = data.data;
          this.configForm = {
            enabled: !!x.enabled,
            cooldownMinutes: Number(x.cooldownMinutes) || 30,
            notifyIfRiskLevelLte: Number(x.notifyIfRiskLevelLte) || 3,
            evalEveryNRounds: Number(x.evalEveryNRounds) || 3,
          };
        }
      });
    },
    saveConfig() {
      this.configSaving = true;
      const payload = {
        enabled: !!this.configForm.enabled,
        cooldownMinutes: this.configForm.cooldownMinutes,
        notifyIfRiskLevelLte: this.configForm.notifyIfRiskLevelLte,
        evalEveryNRounds: this.configForm.evalEveryNRounds,
      };
      Api.admin.saveChildRiskConfig(payload, ({ data }) => {
        this.configSaving = false;
        if (data.code === 0) {
          this.$message.success(this.$t('childRisk.saveOk'));
        }
      });
    },
    fetchRules() {
      this.rulesLoading = true;
      Api.admin.listChildRiskRules(({ data }) => {
        this.rulesLoading = false;
        if (data.code === 0 && Array.isArray(data.data)) {
          this.ruleList = data.data;
        }
      });
    },
    openRuleDialog(row) {
      if (row) {
        this.ruleForm = {
          id: row.id,
          name: row.name || '',
          ruleType: row.ruleType || 'KEYWORD',
          pattern: row.pattern || '',
          riskLevel: row.riskLevel != null ? row.riskLevel : 2,
          category: row.category || 'other',
          sortOrder: row.sortOrder != null ? row.sortOrder : 0,
          status: row.status != null ? row.status : 1,
        };
      } else {
        this.resetRuleForm();
      }
      this.ruleDialogVisible = true;
    },
    resetRuleForm() {
      this.ruleForm = {
        id: null,
        name: '',
        ruleType: 'KEYWORD',
        pattern: '',
        riskLevel: 2,
        category: 'other',
        sortOrder: 0,
        status: 1,
      };
    },
    submitRule() {
      const f = this.ruleForm;
      if (!f.name || !f.pattern) {
        this.$message.warning(this.$t('childRisk.rule.required'));
        return;
      }
      const payload = {
        id: f.id,
        name: f.name.trim(),
        ruleType: f.ruleType,
        pattern: f.pattern,
        riskLevel: f.riskLevel,
        category: (f.category || 'other').trim(),
        sortOrder: f.sortOrder == null ? 0 : f.sortOrder,
        status: f.status,
      };
      this.ruleSaving = true;
      Api.admin.saveChildRiskRule(payload, ({ data }) => {
        this.ruleSaving = false;
        if (data.code === 0) {
          this.$message.success(this.$t('childRisk.saveOk'));
          this.ruleDialogVisible = false;
          this.fetchRules();
        }
      });
    },
    removeRule(row) {
      this.$confirm(this.$t('childRisk.rule.deleteConfirm'), '', {
        type: 'warning',
      })
        .then(() => {
          Api.admin.deleteChildRiskRule(row.id, ({ data }) => {
            if (data.code === 0) {
              this.$message.success(this.$t('childRisk.deleteOk'));
              this.fetchRules();
            }
          });
        })
        .catch(() => {});
    },
    loadEvents(page) {
      const p = page || this.eventPage;
      this.eventPage = p;
      this.eventsLoading = true;
      Api.admin.getChildRiskEventPage(p, this.eventPageSize, ({ data }) => {
        this.eventsLoading = false;
        if (data.code === 0 && data.data) {
          this.eventList = data.data.list || [];
          this.eventTotal = data.data.total || 0;
        }
      });
    },
  },
};
</script>

<style scoped>
.cr-card {
  margin-top: 8px;
}
.toolbar-row {
  margin-bottom: 12px;
}
.form-tip {
  margin-left: 10px;
  color: #909399;
  font-size: 13px;
}
.danger-text {
  color: #f56c6c;
}
.pager-wrap {
  margin-top: 16px;
  text-align: right;
}
</style>
