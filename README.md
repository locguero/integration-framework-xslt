# Integration Framework – XSLT Edition

Apache Camel 4 + Saxon HE XSLT 2.0. Routing decisions and field transformations
unified under a single stylesheet-based approach. No Camunda DMN. No AtlasMap.

## Key Design Decision

Both **routing logic** (which steps to execute) and **field mapping** (firstName→givenName)
are implemented as XSLT 2.0 stylesheets processed by Saxon HE.

Trade-off: simpler tech stack at the cost of the BA drag-and-drop UI.
See `docs/ARCHITECTURE.md` for the full comparison.

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Java | 21+ (Temurin recommended) | `brew install --cask temurin21` |
| Maven | 3.9+ | `brew install maven` |
| Docker | via Colima or Docker Desktop | `brew install colima && colima start` |
| Node.js | 18+ (UI only) | `brew install node` |

---

## Quick Start

### 1. Start infrastructure

```bash
cd docker
docker-compose up -d redis kafka zookeeper
```

Verify all three are running:

```bash
docker ps --format "table {{.Names}}\t{{.Status}}"
```

### 2. Build

```bash
cd ..
mvn clean package -pl framework-http -am -DskipTests
```

### 3. Run the API

```bash
java -jar framework-http/target/framework-http-1.0.0-SNAPSHOT.jar
```

Server starts on **http://localhost:8080**

### 4. Run the UI (optional)

```bash
cd framework-ui
npm install
npm run dev
```

UI available at **http://localhost:3000**

---

## API – Ingest Endpoint

```
POST /api/integration/ingest
```

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | yes | `application/json` |
| `X-Correlation-Id` | yes | Unique ID for idempotency (replays with same ID return `ALREADY_PROCESSED`) |
| `X-Trigger-Id` | no | Trigger type: `HTTP` (default), `CRON` — controls which XSLT routing branch is evaluated |
| `X-Source-System` | yes | Source of the event: `CRM`, `ERP` |
| `X-Entity-Type` | yes | Type of entity: `USER`, `ORDER` |
| `X-Operation` | yes | Operation: `CREATE`, `MODIFY`, `UPDATE`, `DELETE` |
| `Authorization` | no | `Bearer <jwt>` — forwarded to downstream services if present |

### Responses

| Status | Meaning |
|--------|---------|
| `202 Accepted` | Message routed and delivered successfully |
| `200 OK` | Duplicate — already processed (same `X-Correlation-Id`) |
| `422 Unprocessable` | Routing rule rejected the message (e.g. USER DELETE) |
| `500 Internal Server Error` | Unexpected error — check logs |

---

## Curl Examples

### CRM User Create → routes to IAM REST API

XSLT rule: `CRM + USER + CREATE` → `enrich-user, validate-user, transform-to-iam, deliver-rest`

```bash
curl -i -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: crm-user-001" \
  -H "X-Source-System: CRM" \
  -H "X-Entity-Type: USER" \
  -H "X-Operation: CREATE" \
  -d '{
    "id": "u-1",
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane@example.com",
    "role": "admin"
  }'
```

Expected: `202 Accepted` — `{"status":"ACCEPTED","correlationId":"crm-user-001"}`

---

### CRM User Modify → same routing path as Create

```bash
curl -i -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: crm-user-002" \
  -H "X-Source-System: CRM" \
  -H "X-Entity-Type: USER" \
  -H "X-Operation: MODIFY" \
  -d '{
    "id": "u-1",
    "firstName": "Jane",
    "lastName": "Smith",
    "email": "jane.smith@example.com",
    "role": "admin"
  }'
```

---

### ERP Order Create → routes to WMS Kafka topic

XSLT rule: `ERP + ORDER + CREATE` → `validate-order, transform-to-wms, deliver-kafka`

```bash
curl -i -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: erp-order-001" \
  -H "X-Source-System: ERP" \
  -H "X-Entity-Type: ORDER" \
  -H "X-Operation: CREATE" \
  -d '{
    "id": "o-1",
    "item": "widget-pro",
    "qty": 50,
    "warehouse": "WH-NORTH",
    "priority": "HIGH"
  }'
```

Expected: `202 Accepted` — verify delivery:
```bash
docker exec -it docker-kafka-1 kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic wms.orders.inbound \
  --from-beginning
```

---

### ERP Order Update

XSLT rule: `ERP + ORDER + UPDATE` → `validate-order, transform-to-wms, deliver-kafka`

```bash
curl -i -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: erp-order-002" \
  -H "X-Source-System: ERP" \
  -H "X-Entity-Type: ORDER" \
  -H "X-Operation: UPDATE" \
  -d '{
    "id": "o-1",
    "qty": 75,
    "status": "AMENDED"
  }'
```

---

### CRM User Delete → rejected by routing rule

XSLT rule: `CRM + USER + DELETE` → `dead-letter` (422 returned)

```bash
curl -i -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: crm-delete-001" \
  -H "X-Source-System: CRM" \
  -H "X-Entity-Type: USER" \
  -H "X-Operation: DELETE" \
  -d '{"id":"u-1"}'
```

Expected: `422 Unprocessable Entity` — `{"status":"REJECTED","reason":"USER DELETE operations are not permitted via this integration"}`

---

### Duplicate request → idempotency check

Send the same `X-Correlation-Id` a second time:

```bash
curl -i -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: erp-order-001" \
  -H "X-Source-System: ERP" \
  -H "X-Entity-Type: ORDER" \
  -H "X-Operation: CREATE" \
  -d '{"id":"o-1","item":"widget-pro","qty":50}'
```

Expected: `200 OK` — `{"status":"ALREADY_PROCESSED","correlationId":"erp-order-001"}`

---

### CRON Batch → writes to batch_record table

XSLT rule: `triggerId=CRON` → `transform-generic, deliver-db`

The `X-Trigger-Id: CRON` header bypasses the source/entity rules and routes directly to the batch database delivery step. The record is written to the `batch_record` table and can be verified in the H2 console.

```bash
curl -i -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: cron-batch-001" \
  -H "X-Trigger-Id: CRON" \
  -H "X-Source-System: ERP" \
  -H "X-Entity-Type: ORDER" \
  -H "X-Operation: CREATE" \
  -d '{
    "id": "batch-1",
    "orderId": "ORD-101",
    "status": "PENDING",
    "amount": 250.00
  }'
```

Expected: `202 Accepted` — verify the record was written:

```bash
# H2 console at http://localhost:8080/h2-console
SELECT * FROM batch_record;
```

Send multiple batch records (each needs a unique `X-Correlation-Id`):

```bash
for i in 1 2 3; do
  curl -s -X POST http://localhost:8080/api/integration/ingest \
    -H "Content-Type: application/json" \
    -H "X-Correlation-Id: cron-batch-00$i" \
    -H "X-Trigger-Id: CRON" \
    -H "X-Source-System: ERP" \
    -H "X-Entity-Type: ORDER" \
    -H "X-Operation: CREATE" \
    -d "{\"id\":\"batch-$i\",\"orderId\":\"ORD-10$i\",\"status\":\"PENDING\",\"amount\":$((i * 100)).00}"
  echo ""
done
```

> **Note:** In production the `framework-cron` module drives this automatically via a Quartz scheduler (every 5 minutes), polling `ErpPollingAdapter` and creating envelopes with `triggerId=CRON` internally — no HTTP call needed.

---

### Unknown source/entity → falls through to dead-letter

```bash
curl -i -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: unknown-001" \
  -H "X-Source-System: LEGACY" \
  -H "X-Entity-Type: INVOICE" \
  -H "X-Operation: CREATE" \
  -d '{"id":"inv-99"}'
```

Expected: `422 Unprocessable Entity` — `{"status":"REJECTED","reason":"No routing rule matched for this envelope"}`

---

## Admin API

All endpoints are unauthenticated for local development.

### Request log

```bash
# List recent requests (paginated)
curl -s http://localhost:8080/admin/requests | python3 -m json.tool

# Specific request by correlation ID
curl -s http://localhost:8080/admin/requests/erp-order-001 | python3 -m json.tool

# Summary counts + hourly chart data
curl -s http://localhost:8080/admin/requests/stats | python3 -m json.tool
```

### XSLT version management

```bash
# List all XSLT files and their versions
curl -s http://localhost:8080/admin/xslt | python3 -m json.tool

# List all versions for a specific file
curl -s http://localhost:8080/admin/xslt/routing-decision.xsl | python3 -m json.tool

# Upload a new version (body = raw XSL content)
curl -X POST "http://localhost:8080/admin/xslt/routing-decision.xsl?comment=Add+new+rule" \
  -H "Content-Type: text/plain" \
  --data-binary @framework-xslt/src/main/resources/xslt/routing/routing-decision.xsl

# Roll back to a previous version
curl -X PUT http://localhost:8080/admin/xslt/routing-decision.xsl/activate/1
```

### H2 database console

```
http://localhost:8080/h2-console
JDBC URL:  jdbc:h2:file:./framework-data/db
Username:  sa
Password:  (leave blank)
```

---

## Modules

| Module | Role |
|--------|------|
| `framework-core` | IntegrationEnvelope model, IdempotencyFilter (Redis), SecurityContextExtractor (JWT), RoutingSlipExecutor |
| `framework-xslt` | Saxon HE engine, 6 XSLT stylesheets, Camel step routes for validation and transformation |
| `framework-http` | HTTP ingest route, EnrichmentStep, RequestLog audit, XSLT version store, Admin API |
| `framework-kafka` | Kafka consumer trigger, SEDA backpressure, dead-letter topic |
| `framework-cron` | Cron poll trigger, watermark tracking, ERP adapter |
| `framework-delivery` | REST / Kafka / DB / hold queue / retry delivery steps |
| `framework-ui` | React dashboard (Vite + Recharts) — request log table, charts, XSLT admin |

---

## XSLT Routing Rules

| Source | Entity | Operation | Steps |
|--------|--------|-----------|-------|
| `CRM` | `USER` | `CREATE`, `MODIFY` | enrich-user → validate-user → transform-to-iam → deliver-rest |
| `CRM` | `USER` | `DELETE` | dead-letter (rejected) |
| `ERP` | `ORDER` | `CREATE` | validate-order → transform-to-wms → deliver-kafka (DURABLE) |
| `ERP` | `ORDER` | `UPDATE` | validate-order → transform-to-wms → deliver-kafka |
| `CRON` | any | any | transform-generic → deliver-db |
| *(any)* | *(any)* | *(any)* | dead-letter (rejected — no rule matched) |

**Fallback routing** (enrichment circuit breaker open):

| SLA Class | Steps |
|-----------|-------|
| PRIORITY (CRM/USER) | deliver-async-hold → alert-ops |
| STANDARD (all others) | deliver-retry-queue |

---

## Running Tests

```bash
# XSLT unit tests only (no Spring context, fast)
mvn test -pl framework-xslt

# All tests
mvn test
```

---

## Architecture Diagram

See `integration-flow.drawio` — open with the draw.io VS Code extension or at [app.diagrams.net](https://app.diagrams.net).
