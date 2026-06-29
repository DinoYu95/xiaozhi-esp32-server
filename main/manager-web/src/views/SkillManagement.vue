<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">技能管理（多角色智伴）</h2>
      <el-button v-if="activeTab === 'admin'" type="primary" @click="openAdd">新建技能</el-button>
    </div>
    <div class="main-wrapper">
      <el-tabs v-model="activeTab" @tab-click="onTabClick">
        <el-tab-pane label="管理员技能" name="admin">
          <el-table :data="skillList" border style="width: 100%">
            <el-table-column prop="id" label="技能ID" width="180" />
            <el-table-column prop="name" label="名称" width="120" />
            <el-table-column prop="description" label="说明" show-overflow-tooltip />
            <el-table-column label="官方推荐" width="100" align="center">
              <template slot-scope="scope">
                <el-tag :type="scope.row.isOfficialRecommended === 1 ? 'success' : 'info'" size="small">
                  {{ scope.row.isOfficialRecommended === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="默认兜底" width="100" align="center">
              <template slot-scope="scope">
                <el-tag :type="scope.row.isDefaultFallback === 1 ? 'warning' : 'info'" size="small">
                  {{ scope.row.isDefaultFallback === 1 ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template slot-scope="scope">
                <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
                <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="家长端技能" name="parent">
          <el-table :data="parentSkillList" border style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="parentNickname" label="添加者" width="120" />
            <el-table-column prop="name" label="名称" width="120" />
            <el-table-column prop="description" label="说明" show-overflow-tooltip />
            <el-table-column prop="createTime" label="创建时间" width="170">
              <template slot-scope="scope">
                {{ scope.row.createTime ? formatDate(scope.row.createTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template slot-scope="scope">
                <el-button size="small" type="danger" @click="handleDeleteParentSkill(scope.row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
    <el-dialog :title="editingId ? '编辑技能' : '新建技能'" :visible.sync="dialogVisible" width="560px">
      <el-form ref="skillForm" :model="form" label-width="100px">
        <el-form-item label="技能ID" required>
          <el-input v-model="form.id" :disabled="!!editingId" placeholder="如 skill_children_chat" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="如 儿童对话" />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" rows="2" placeholder="简短用途说明" />
        </el-form-item>
        <el-form-item label="系统提示" required>
          <el-input v-model="form.instructions" type="textarea" rows="6" placeholder="技能说明/系统级提示，发给模型" />
        </el-form-item>
        <el-form-item label="版本">
          <el-input v-model="form.version" placeholder="如 1.0" />
        </el-form-item>
        <el-form-item label="官方推荐">
          <el-switch v-model="form.isOfficialRecommended" active-text="是" inactive-text="否" />
        </el-form-item>
        <el-form-item label="默认兜底">
          <el-switch v-model="form.isDefaultFallback" active-text="是" inactive-text="否" />
          <div class="form-tip">意图未匹配任何已绑定技能时使用；全平台仅一个</div>
        </el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSkill">保存</el-button>
      </span>
    </el-dialog>
  </div>
</template>

<script>
import Api from "@/apis/api";
import HeaderBar from "@/components/HeaderBar.vue";

export default {
  name: "SkillManagement",
  components: { HeaderBar },
  data() {
    return {
      activeTab: 'admin',
      skillList: [],
      parentSkillList: [],
      dialogVisible: false,
      editingId: null,
      form: {
        id: "",
        name: "",
        description: "",
        instructions: "",
        version: "1.0",
        isOfficialRecommended: false,
        isDefaultFallback: false,
      },
    };
  },
  mounted() {
    this.fetchList();
    this.fetchParentList();
  },
  methods: {
    onTabClick(tab) {
      if (tab.name === 'parent') this.fetchParentList();
      else this.fetchList();
    },
    formatDate(val) {
      if (!val) return '-';
      const d = new Date(val);
      return d.toLocaleString('zh-CN');
    },
    fetchList() {
      Api.agent.getSkillList(({ data }) => {
        if (data.code === 0 && data.data) this.skillList = data.data;
      });
    },
    fetchParentList() {
      Api.agent.getParentSkillList(({ data }) => {
        if (data.code === 0 && data.data) this.parentSkillList = data.data;
      });
    },
    openAdd() {
      this.editingId = null;
      this.form = { id: "", name: "", description: "", instructions: "", version: "1.0", isOfficialRecommended: false, isDefaultFallback: false };
      this.dialogVisible = true;
    },
    openEdit(row) {
      this.editingId = row.id;
      this.form = {
        id: row.id,
        name: row.name,
        description: row.description || "",
        instructions: row.instructions || "",
        version: row.version || "1.0",
        isOfficialRecommended: row.isOfficialRecommended === 1,
        isDefaultFallback: row.isDefaultFallback === 1,
      };
      this.dialogVisible = true;
    },
    submitSkill() {
      if (!this.form.id || !this.form.name || !this.form.instructions) {
        this.$message.warning("请填写技能ID、名称和系统提示");
        return;
      }
      if (this.editingId) {
        Api.agent.updateAgentSkill(this.form, ({ data }) => {
          if (data.code === 0) {
            this.$message.success("保存成功");
            this.dialogVisible = false;
            this.fetchList();
          } else this.$message.error(data.msg || "保存失败");
        });
      } else {
        Api.agent.addAgentSkill(this.form, ({ data }) => {
          if (data.code === 0) {
            this.$message.success("创建成功");
            this.dialogVisible = false;
            this.fetchList();
          } else this.$message.error(data.msg || "创建失败");
        });
      }
    },
    handleDelete(row) {
      this.$confirm("确定删除该技能？", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      }).then(() => {
        Api.agent.deleteAgentSkill(row.id, ({ data }) => {
          if (data.code === 0) {
            this.$message.success("已删除");
            this.fetchList();
          } else this.$message.error(data.msg || "删除失败");
        });
      }).catch(() => {});
    },
    handleDeleteParentSkill(row) {
      this.$confirm('确定删除该家长端技能？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }).then(() => {
        Api.agent.deleteParentSkill(row.id, ({ data }) => {
          if (data.code === 0) {
            this.$message.success('已删除');
            this.fetchParentList();
          } else this.$message.error(data.msg || '删除失败');
        });
      }).catch(() => {});
    },
  },
};
</script>

<style scoped>
.operation-bar { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; }
.page-title { margin: 0; font-size: 18px; }
.main-wrapper { padding: 0 20px 20px; }
</style>
