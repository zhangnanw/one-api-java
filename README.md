# one-api-java

LLM API 网关，Java 17+，单 shaded jar。监听 13000 端口。OpenAI 协议兼容。
**核心特性：API 表面只暴露虚拟模型，不暴露具体物理实例。**

## 跑

```bat
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
mvnw package -DskipTests
java -jar target\one-api-java-1.0.0-shaded.jar
```

## 配置

YAML 文件位置（按查找顺序）：

1. `~/.one-api/config.yaml`（用户级）
2. classpath `config.yaml`（打包内嵌）

最小配置示例：

```yaml
server:
  port: 13000
relay:
  maxRetries: 2
  cacheTtlSeconds: 10
  layerOrder: [free, subscription, payg]
  requireVirtualModel: true
database:
  path: ~/.one-api/one-api.db
policies:
  reasoning:
    triggerSuffix: "-max"
```

**关键字段**：

- `relay.requireVirtualModel`（默认 `true`）：未命中虚拟模型直接 404，不允许按物理 model_name 兜底
- `database.path`：缺省 `~/.one-api/one-api.db`
- `policies.reasoning.triggerSuffix`：`-max` 后缀触发 reasoning 模式

## 怎么加 vendor

```bash
curl -X POST http://localhost:13000/api/vendors \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MyProvider",
    "baseUrl": "https://api.example.com/v1",
    "apiKey": "sk-xxx",
    "type": "openai",
    "group": "default"
  }'
```

## 怎么加 instance

```bash
curl -X POST http://localhost:13000/api/instances \
  -H "Content-Type: application/json" \
  -d '{
    "vendorId": 1,
    "modelName": "gpt-4o",
    "upstreamModel": "gpt-4o-2024-08-06",
    "meta": "{\"max_tokens\":16384}"
  }'
```

## 怎么加虚拟模型

```bash
curl -X POST http://localhost:13000/api/virtual-models \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my-gpt-4o",
    "match": "{\"type\":\"CapabilityMatch\",\"required\":[\"vision\"]}"
  }'
```

调用时用 `my-gpt-4o` 这个名字请求，one-api 内部按匹配规则路由到上游。

## API 端点

| 路径 | 用途 |
|---|---|
| `POST /v1/chat/completions` | OpenAI Chat Completions（**唯一对外公开**的模型端点） |
| `GET /v1/models` | 列出所有虚拟模型（**不包含**物理实例） |
| `GET /api/status` | 启动时间 / 版本 / cooldown 缓存指标 |
| `GET/POST/PUT/DELETE /api/vendors` | 供应商管理 |
| `GET/POST/PUT/DELETE /api/instances` | 物理实例管理 |
| `GET/POST/PUT/DELETE /api/virtual-models` | 虚拟模型管理 |
| `POST /api/vendors/refresh-models` | 从上游拉取模型列表 |

## 架构

V2 5 阶段中继流水线：

```txt
请求 → RequestParser（解析 model/body）
     → stage 2 过滤器链（CapabilityRequirementMarker / VirtualModelLookup / NameMatcher / TagFilter）
     → RouterService.loadCandidates（从 Instance 表查候选）
     → stage 3 过滤器链（CooldownFilter / LayerFilter / CapabilityInstanceFilter / ActiveStatusFilter）
     → Comparator 排序（ByScore / ByStatusDesc / ById）
     → DefaultRelay 中继（→ UpstreamClient）
```

### 关键设计原则

**API 表面只暴露虚拟模型，不暴露具体物理实例。** 用户请求 `my-gpt-4o`，one-api 内部按 `VirtualModel.match` JSON 规则匹配到一个或多个 `Instance`，再按 pref/layer/status 排序选最优。

如果用户**直接**请求一个 `Instance.modelName`（物理名），`relay.requireVirtualModel=true` 时直接 404 — 因为设计原则是不让用户绕过虚拟模型直接指定实例。

## 怎么跑测试

```bash
mvnw test -Dtest=VirtualModelLookupTest
```

## 进一步阅读

- [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md) — 需求规格
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — 路线图与已实现项
- [`docs/CODE_STYLE.md`](docs/CODE_STYLE.md) — 代码风格（record vs Lombok）
