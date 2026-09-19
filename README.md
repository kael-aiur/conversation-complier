# conversation-compiler

Spring Boot + Vue 3 的会话编译器 Web 项目骨架，前后端结构参考 `geo-helper-parent`。

## 目录结构

- `src/main/java`：Spring Boot 后端源码
- `src/main/resources/static`：嵌入 Spring Boot 的前端构建产物
- `frontend`：Vue 3 + Vite 前端源码，使用 pnpm

## 开发

```bash
cd frontend
pnpm install
pnpm dev
```

前端开发服务器默认运行在 `http://127.0.0.1:5173`，`/api` 请求代理到 Spring Boot 的 `http://127.0.0.1:8080`。

## 构建

```bash
cd frontend
pnpm install

# 构建到 Spring Boot 页面目录：src/main/resources/static
pnpm build:spring

# 构建到前端独立部署目录：frontend/dist
pnpm build:standalone
```

`pnpm build` 默认等同于 `pnpm build:standalone`。

## 运行后端

```bash
mvn spring-boot:run
# 或先构建再运行
mvn clean package
java -jar target/conversation-compiler-0.0.1-SNAPSHOT.jar
```

后端提供示例健康检查接口：`GET /api/health`。
