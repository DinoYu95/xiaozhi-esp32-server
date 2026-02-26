# 数据库 changelog 与 SQL 执行说明

项目用 **Liquibase** 做数据库版本管理：**谁在什么时候执行、带日期的 SQL 和 changelog 是干啥的**，看这一份就够。

---

## 一、changelog 是啥？

- **changelog** = 一整套「数据库变更记录」的清单。
- 项目里有一个**总清单**：`src/main/resources/db/changelog/db.changelog-master.yaml`。
- 里面按顺序列了很多 **changeSet**，每个 changeSet 指向**一个 SQL 文件**（例如 `202602021555.sql`）。
- Liquibase 会**按顺序**执行这些 changeSet；每个 changeSet 有唯一 **id**（就是文件名里那串数字），**执行过一次就会在库里记下来，以后不会再执行**。

所以：**changelog = 一列「要执行的 SQL」的清单；每个 SQL 文件 = 一次变更（建表、加字段、插数据等）。**

---

## 二、文件名里的日期（如 202602021555）是干啥的？

- 文件名里的数字就是 **changeSet 的 id**（在 `db.changelog-master.yaml` 里要和文件名一致）。
- 规范是**按时间取名**，例如 `202602021555` = 2026年02月02日 15:55，这样：
  1. **顺序清楚**：新加的 SQL 排在后面，执行顺序不会乱；
  2. **不会重名**：每人加自己的时间点，id 不冲突；
  3. **好排查**：一看文件名就知道大概是哪天加的变更。

**总结**：带日期的文件名 = 这条变更的唯一 id + 大致时间，**不是**「到了这个日期才执行」。

---

## 三、什么时候执行？

- **应用启动时**执行。
- manager-api 是 Spring Boot 项目，引入了 **liquibase-core** 依赖；启动时会自动：
  1. 读 `db.changelog-master.yaml`；
  2. 按顺序看每个 changeSet；
  3. 在数据库里查一张 Liquibase 自己的表（如 `databasechangelog`），看这个 changeSet 的 id 有没有执行过；
  4. **没执行过** → 执行对应的 SQL 文件，然后把这次执行记录写进 `databasechangelog`；
  5. **执行过** → 跳过。

所以：**每次启动 manager-api，Liquibase 都会跑一遍，但只会执行「还没执行过」的 changeSet。** 新加一个带日期的 SQL 并挂在 master 后面，下次启动就会自动执行这一次。

---

## 四、怎么新加一条变更？（加表、加字段都用这套）

1. **新建一个 SQL 文件**  
   路径：`src/main/resources/db/changelog/`，文件名用**时间当 id**，例如 `202602131000.sql`（表示 2026-02-13 10:00）。  
   里面写普通 SQL（建表、ALTER、INSERT 等）即可。

2. **在总清单里挂上这条变更**  
   打开 `db.changelog-master.yaml`，在**最后**（注意顺序）加一段：

   ```yaml
   - changeSet:
       id: 202602131000
       author: 你的名字或团队
       changes:
         - sqlFile:
             encoding: utf8
             path: classpath:db/changelog/202602131000.sql
   ```

3. **启动应用**  
   下次启动 manager-api 时，Liquibase 会执行这个新的 changeSet，执行完后会在 `databasechangelog` 里记一笔，以后不会再执行。

**注意**：规范要求**不要改已经执行过的 changeSet 或旧 SQL 文件**，只允许**新增**新的 changeSet；否则已部署的环境会乱（有的执行过、有的没执行过）。

---

## 五、一句话总结

| 问题 | 答案 |
|------|------|
| changelog 是啥？ | 一列「数据库变更」的清单（master 文件 + 一堆 SQL 文件）。 |
| 带日期的 SQL 文件名是啥？ | 这条变更的唯一 id，用时间命名便于排序、不重名。 |
| 什么时候执行？ | **每次启动 manager-api 时**；只执行「还没执行过」的 changeSet。 |
| 想加新表/新字段？ | 新建一个带日期的 .sql 文件，在 db.changelog-master.yaml 末尾加一个 changeSet 指向它，启动即可。 |
