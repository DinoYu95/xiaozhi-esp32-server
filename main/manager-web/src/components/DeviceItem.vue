<template>
  <div class="device-item" :class="{ 'device-item--active': device.parentActivated, 'device-item--inactive': !device.parentActivated }">
    <div style="display: flex;justify-content: space-between;align-items: flex-start;">
      <div class="title-row">
        <div style="font-weight: 700;font-size: 18px;text-align: left;color: #3d4566;">
          {{ primaryTitle }}
        </div>
        <span v-if="device.parentActivated" class="status-badge status-badge--active">{{ $t('home.parentActivated') }}</span>
        <span v-else class="status-badge status-badge--inactive">{{ $t('home.parentInactive') }}</span>
      </div>
      <div v-if="showAgentSubname" class="agent-subname">
        {{ $t('home.agentInternalName') }}：{{ device.agentName }}
      </div>
      <div>
        <img src="@/assets/home/delete.png" alt="" style="width: 18px;height: 18px;margin-right: 10px;"
          @click.stop="handleDelete" />
        <el-tooltip class="item" effect="dark" :content="device.systemPrompt" placement="top"
          popper-class="custom-tooltip">
          <img src="@/assets/home/info.png" alt="" style="width: 18px;height: 18px;" />
        </el-tooltip>
      </div>
    </div>
    <div class="device-name">
      {{ $t('home.languageModel') }}：{{ device.llmModelName }}
    </div>
    <div v-if="device.parentActivated" class="parent-info">
      {{ $t('home.boundParent') }}：{{ device.ownerParentNickname || '--' }}
      <span v-if="device.boundParentCount > 1">（{{ $t('home.parentMemberCount', { count: device.boundParentCount }) }}）</span>
    </div>
    <div v-else class="parent-info parent-info--muted">
      {{ $t('home.noBoundParentHint') }}
    </div>
    <div class="device-name">
      {{ $t('home.voiceModel') }}：{{ device.ttsModelName }} ({{ device.ttsVoiceName }})
    </div>
    <div style="display: flex;gap: 10px;align-items: center;flex-wrap: wrap;">
      <div class="settings-btn" @click="handleConfigure">
        {{ $t('home.configureRole') }}
      </div>
      <div v-if="featureStatus.voiceprintRecognition" class="settings-btn" @click="handleVoicePrint">
        {{ $t('home.voiceprintRecognition') }}
      </div>
      <div class="settings-btn" @click="handleDeviceManage">
        {{ $t('home.deviceManagement') }}({{ device.deviceCount }})
      </div>
      <div v-if="device.deviceCount > 0" :class="['settings-btn', device.parentActivated ? 'update-parent-btn' : 'bind-parent-btn']" @click="handleBindParent">
        {{ device.parentActivated ? $t('home.updateParent') : $t('home.bindParent') }}
      </div>
      <div :class="['settings-btn', { 'disabled-btn': !device.chatHistoryConf }]"
        @click="handleChatHistory">
        <el-tooltip v-if="!device.chatHistoryConf" :content="$t('home.enableChatHistory')" placement="top">
          <span>{{ $t('home.chatHistory') }}</span>
        </el-tooltip>
        <span v-else>{{ $t('home.chatHistory') }}</span>
      </div>
    </div>
    <div class="version-info">
      <div>{{ $t('home.lastConversation') }}：{{ formattedLastConnectedTime }}</div>
    </div>
  </div>
</template>

<script>
import i18n from '@/i18n';

export default {
  name: 'DeviceItem',
  props: {
    device: { type: Object, required: true },
    featureStatus: { 
      type: Object, 
      default: () => ({
        voiceprintRecognition: false,
        voiceClone: false,
        knowledgeBase: false
      })
    }
  },
  data() {
    return { switchValue: false }
  },
  computed: {
    primaryTitle() {
      return this.device.parentDeviceDisplayName || this.device.agentName
    },
    showAgentSubname() {
      const displayName = this.device.parentDeviceDisplayName
      return displayName && displayName !== this.device.agentName
    },
    formattedLastConnectedTime() {
      if (!this.device.lastConnectedAt) return this.$t('home.noConversation');

      const lastTime = new Date(this.device.lastConnectedAt);
      const now = new Date();
      const diffMinutes = Math.floor((now - lastTime) / (1000 * 60));

      if (diffMinutes <= 1) {
        return this.$t('home.justNow');
      } else if (diffMinutes < 60) {
        return this.$t('home.minutesAgo', { minutes: diffMinutes });
      } else if (diffMinutes < 24 * 60) {
        const hours = Math.floor(diffMinutes / 60);
        const minutes = diffMinutes % 60;
        return this.$t('home.hoursAgo', { hours, minutes });
      } else {
        return this.device.lastConnectedAt;
      }
    }
  },
  methods: {
    handleDelete() {
      this.$emit('delete', this.device.agentId)
    },
    handleConfigure() {
      this.$router.push({ path: '/role-config', query: { agentId: this.device.agentId } });
    },
    handleVoicePrint() {
      this.$router.push({ path: '/voice-print', query: { agentId: this.device.agentId } });
    },
    handleDeviceManage() {
      this.$router.push({ path: '/device-management', query: { agentId: this.device.agentId } });
    },
    handleChatHistory() {
      if (!this.device.chatHistoryConf) {
        return
      }
      this.$emit('chat-history', { agentId: this.device.agentId, agentName: this.device.agentName })
    },
    handleBindParent() {
      this.$emit('bind-parent', this.device)
    }
  }
}
</script>
<style scoped>
.device-item {
  width: 342px;
  border-radius: 20px;
  background: #fafcfe;
  padding: 22px;
  box-sizing: border-box;
  border: 2px solid transparent;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.device-item--active {
  border-color: #67c23a;
  box-shadow: 0 6px 18px rgba(103, 194, 58, 0.12);
  background: linear-gradient(180deg, #f8fff8 0%, #fafcfe 100%);
}

.device-item--inactive {
  border-color: #e4e7ed;
  background: #f5f7fa;
  opacity: 0.96;
}

.title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  max-width: calc(100% - 50px);
}

.status-badge {
  font-size: 11px;
  line-height: 18px;
  padding: 0 8px;
  border-radius: 10px;
  font-weight: 600;
}

.status-badge--active {
  color: #389e0d;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}

.status-badge--inactive {
  color: #909399;
  background: #f4f4f5;
  border: 1px solid #dcdfe6;
}

.parent-info {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #409eff;
  text-align: left;
}

.parent-info--muted {
  color: #909399;
}

.agent-subname {
  margin: 2px 0 6px;
  font-size: 12px;
  color: #909399;
  text-align: left;
  line-height: 1.4;
}

.bind-parent-btn {
  background: #fff7e6;
  color: #fa8c16;
}

.update-parent-btn {
  background: #ecf5ff;
  color: #409eff;
}

.device-name {
  margin: 7px 0 10px;
  font-weight: 400;
  font-size: 11px;
  color: #3d4566;
  text-align: left;
}

.settings-btn {
  font-weight: 500;
  font-size: 12px;
  color: #5778ff;
  background: #e6ebff;
  width: auto;
  padding: 0 12px;
  height: 21px;
  line-height: 21px;
  cursor: pointer;
  border-radius: 14px;
}

.version-info {
  display: flex;
  justify-content: space-between;
  margin-top: 15px;
  font-size: 12px;
  color: #979db1;
  font-weight: 400;
}

.disabled-btn {
  background: #e6e6e6;
  color: #999;
  cursor: not-allowed;
}
</style>

<style>
.custom-tooltip {
  max-width: 400px;
  word-break: break-word;
}
</style>