# Silence Job Server - 项目上下文文档

## 项目概述
- **项目名称**：silence-job-server（分布式任务调度服务器）
- **Java 版本**：21
- **Parent POM**：platform-parent (2.0.1-SNAPSHOT)
- **公共依赖版本**：silence-job-common (1.8.0-SNAPSHOT)

## 项目结构
```
silence-job-server/
├── silence-job-server-app/              # 应用层（API、Controller）
├── silence-job-server-common/           # 本地公共工具类
├── silence-job-server-core/             # 核心业务逻辑、Mapper、Service
│   └── resources/mapper/                # MyBatis XML映射文件
├── silence-job-server-core-model/       # 数据模型
├── silence-job-server-core-repository/  # 数据访问层
├── silence-job-server-core-service/     # 业务服务层
├── silence-job-server-job-task/         # 工作任务处理
├── silence-job-server-retry-task/       # 重试任务处理
├── silence-job-server-rpc/              # RPC 通信
├── silence-job-server-scheduler/        # 调度器
├── silence-job-server-support/          # 支持组件
├── silence-job-server-task-common/      # 任务公共组件
└── silence-job-server-starter/          # Spring Boot Starter 入口
    └── resources/
        ├── application.yml              # 本地配置
        └── 其他配置文件
```

## 关键配置

### 数据库枚举处理
**问题**：枚举字段数据库转换失败  
**根本原因**：缺少 `platform-cloud-config` 依赖  
**解决方案**：
- 添加依赖：`platform-cloud-config` (2.0.1-SNAPSHOT)
- 该依赖通过 Spring Boot 自动配置加载 MyBatis Plus 的枚举处理器

**关键配置（来自 platform-cloud-config）**：
```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    default-enum-type-handler: com.old.silence.data.commons.handler.GenericEnumTypeHandler
  type-handlers-package: com.old.silence.data.commons.handler
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: is_deleted
      logic-not-delete-value: 0
      logic-delete-value: 1
```

**枚举处理规则**：
- 所有枚举必须实现 `EnumValue<?>` 接口（来自 silence-job-common）
- 数据库存储枚举的具体值（通过 EnumValue.getValue()）
- GenericEnumTypeHandler 自动完成 Java enum ↔ Database 转换

### 外部枚举源
- **来源**：`com.old.silence.job.common.enums`（silence-job-common 依赖）
- **包括**：ExecutorType, JobArgsType, JobState, RetryState, TaskState 等
- **这些枚举已实现 EnumValue 接口**

## 重要依赖关系
- `platform-parent`：父 POM，管理各种公共依赖
- `platform-cloud-config`：平台共享配置（必须）
- `platform-data-commons`：数据通用工具
- `platform-data-mybatis-plus`：MyBatis Plus 增强
- `silence-job-common-*`：框架通用模块

## MyBatis 配置
- **Mapper 位置**：`classpath:/mapper/**/*Mapper.xml`
- **数据源**：MySQL（配置在 application.yml）
- **Liquibase**：数据库版本管理（在 platform-cloud-config 中配置，本地开发关闭）

## 启动类
- 入口：`silence-job-server-starter` 模块
- Main Class：`com.old.silence.job.server.SilenceJobCenterApplication`
- 端口：8098（本地），38080（生产）

## 编码规范注意
1. **Mapper 文件命名**：遵循 `*Mapper.xml` 格式
2. **Mapper 接口命名**：遵循 `*Mapper` 格式，放在 `com.old.silence.job.server.*.mapper` 包下
3. **Service 类命名**：遵循 `*Service` 或 `*Impl` 格式
4. **枚举字段在数据库中存储值**，通过 TypeHandler 完成转换

## 常见问题排查
1. **枚举转换异常** → 检查 platform-cloud-config 依赖是否添加
2. **找不到 Mapper** → 检查 XML 位置是否正确，XML namespace 是否对应接口全路径
3. **驼峰映射失败** → 检查 MyBatis Plus 配置中 map-underscore-to-camel-case 是否启用
4. **逻辑删除不生效** → 检查字段是否为 is_deleted，Entity 类是否用 @TableLogic 注解

## 运行命令
```bash
# 本地开发构建
mvn clean package -DskipTests

# 启动（通过 starter 模块）
mvn spring-boot:run -pl silence-job-server-starter
```

---
*最后更新*：2026-02-05
