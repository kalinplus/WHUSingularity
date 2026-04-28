# Docker 弹性伸缩脚本说明

本文档说明 `deploy/` 目录下与容器弹性伸缩相关的脚本用法，供上层 autoscaler 服务或手动运维调用。

## 1. 脚本总览

### 1.1 统一入口（推荐）

- `deploy/scale-instance.ps1`

统一入口会根据 `-Service` 和 `-Action` 分发到对应子脚本执行，并输出机器可读 JSON。

### 1.2 服务级子脚本

#### order
- `deploy/start-next-order-instance.ps1`
- `deploy/remove-last-order-instance.ps1`

#### user
- `deploy/start-next-user-instance.ps1`
- `deploy/remove-last-user-instance.ps1`

#### stock
- `deploy/start-next-stock-instance.ps1`
- `deploy/remove-last-stock-instance.ps1`

---

## 2. 统一入口参数

脚本：`deploy/scale-instance.ps1`

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\scale-instance.ps1 \
  -Service order -Action start
```

参数说明：

- `-Service`（必填）：`order | user | stock`
- `-Action`（必填）：`start | remove`
- `-Index`（可选）：指定实例编号
  - `start`：不传则自动取下一个可用编号
  - `remove`：不传则默认删除编号最大的实例
- `-Port`（可选，仅 `start`）：指定宿主机/容器端口
- `-MinInstances`（可选，仅 `remove`）：删除保护阈值，默认 `1`
- `-DryRun`（可选）：仅输出计划，不实际执行

---

## 3. 返回结果（机器可读 JSON）

所有脚本都输出一行 JSON，方便上层服务解析。

### 3.1 启动成功示例

```json
{"action":"start","service":"order","container":"singularity-order-3","success":true,"dryRun":false,"port":8087,"index":3}
```

### 3.2 删除成功示例

```json
{"action":"remove","service":"user","removed":true,"container":"singularity-user-3","success":true,"index":3,"dryRun":false}
```

### 3.3 删除被保护示例（触发最小实例数）

```json
{"success":true,"action":"remove","service":"stock","removed":false,"reason":"min_instances_protected","minInstances":1,"current":1}
```

### 3.4 失败示例

```json
{"success":false,"service":"order","action":"start","error":"Container already exists: singularity-order-3"}
```

---

## 4. 常用调用示例

### 4.1 扩容（自动编号和端口）

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\scale-instance.ps1 -Service order -Action start
```

### 4.2 扩容（指定编号和端口）

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\scale-instance.ps1 -Service stock -Action start -Index 6 -Port 8186
```

### 4.3 缩容（默认删最后一个，至少保留 1 个）

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\scale-instance.ps1 -Service user -Action remove -MinInstances 1
```

### 4.4 缩容（指定编号）

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\scale-instance.ps1 -Service order -Action remove -Index 5 -MinInstances 1
```

### 4.5 DryRun（演练）

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy\scale-instance.ps1 -Service order -Action start -DryRun
```

---

## 5. 运行前置条件

- 已安装 Docker Desktop，并可正常执行 `docker` 命令
- 已启动基础编排（至少存在网络 `deploy_default`）：

```powershell
docker compose -f deploy/docker-compose.backend.yml up -d
```

- 对应服务 jar 已提前构建（脚本不会自动编译）：

```powershell
mvn -pl singularity-order,singularity-user,singularity-stock -am clean package -DskipTests
```

---

## 6. 端口分配策略（自动模式）

- `order`：从 `8081` 开始按奇数扫描可用端口
- `user`：从 `8093` 开始扫描可用端口
- `stock`：从 `8087` 开始扫描可用端口

如果需要固定策略（例如仅允许某个端口池），建议上层服务显式传入 `-Port`。

---

## 7. 接入建议（给上层 autoscaler）

建议按以下顺序处理：

1. 先用 `-DryRun` 获取预期动作
2. 校验策略（副本上下限、冷却时间等）
3. 去掉 `-DryRun` 正式执行
4. 解析 JSON 并记录审计日志

若要高并发调用，建议在上层服务做分布式锁，避免并发重复扩缩同一服务。
