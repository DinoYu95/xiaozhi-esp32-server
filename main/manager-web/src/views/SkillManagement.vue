<template>
  <div class="welcome">
    <HeaderBar />
    <div class="operation-bar">
      <h2 class="page-title">技能管理（多角色智伴）</h2>
      <el-button type="primary" @click="openAdd">新建技能</el-button>
    </div>
    <div class="main-wrapper">
      <el-table :data="skillList" border style="width: 100%">
        <el-table-column prop="id" label="技能ID" width="180" />
        <el-table-column prop="name" label="名称" width="120" />
        <el-table-column prop="description" label="说明" show-overflow-tooltip />
        <el-table-column label="操作" width="160">
          <template slot-scope="scope">
            <el-button size="small" @click="openEdit(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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
      skillList: [],
      dialogVisible: false,
      editingId: null,
      form: {
        id: "",
        name: "",
        description: "",
        instructions: "",
        version: "1.0",
      },
    };
  },
  mounted() {
    this.fetchList();
  },
  methods: {
    fetchList() {
      Api.agent.getSkillList(({ data }) => {
        if (data.code === 0 && data.data) this.skillList = data.data;
      });
    },
    openAdd() {
      this.editingId = null;
      this.form = { id: "", name: "", description: "", instructions: "", version: "1.0" };
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
  },
};
</script>

<style scoped>
.operation-bar { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; }
.page-title { margin: 0; font-size: 18px; }
.main-wrapper { padding: 0 20px 20px; }
</style>
