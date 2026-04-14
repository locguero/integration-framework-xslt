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
  -H "X-Correlation-Id: erp-order-003" \
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
  -H "X-Correlation-Id: erp-order-004" \
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

### CRON Batch → simulated via HTTP (dev/test only)

The scheduler fires automatically (see [Scheduled CRON Flow](#scheduled-cron-flow) below). During
development you can simulate a single CRON record by sending an HTTP request with
`X-Trigger-Id: CRON` plus the source/entity/operation values that match an active
`CronRequestType` row:

```bash
# Simulates what the scheduler produces for an ERP ORDER CREATE request type
curl -i -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: cron-batch-006" \
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

Expected: `202 Accepted` — XSLT rule `CRON + ERP/ORDER/CREATE` calls
`erp-order-steps(execMode=BATCH)` → `validate-order,transform-to-wms,deliver-kafka`.

Send multiple records (each needs a unique `X-Correlation-Id`):

```bash
for i in 4 5 6; do
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

## Scheduled CRON Flow

The `framework-cron` module runs a **Quartz-backed scheduler** that fires on a configurable
cron expression (default: **every hour**).  Each tick it loads the active
`CronRequestType` rows from the database, calls the ERP adapter once per type,
and pushes every returned record through the same integration pipeline as HTTP
requests.

### Schedule configuration

Edit `framework-cron/src/main/resources/application-cron.yml` and restart:

```yaml
framework:
  cron:
    erp-poll:
      cron: "0 0 * * * ?"       # every hour (default)
      # cron: "0 */30 * * * ?"  # every 30 minutes
      # cron: "0 */5 * * * ?"   # every 5 minutes (local dev)
      # cron: "0 0 2 * * ?"     # daily at 02:00
```

The expression uses Quartz cron syntax (6 fields: second minute hour day month weekday).

### End-to-end flow

```
Quartz scheduler (every hour)
  │
  ├─ reads cron_request_type WHERE active = true   ← managed in Admin UI
  │
  ├─ for each active type (e.g. ERP / ORDER / CREATE):
  │     ErpPollingAdapter.fetchPendingRecords(watermark)
  │     ↓  returns List<Map>
  │     split → seda:cron-processing (bounded, 2 concurrent consumers)
  │     ↓
  │     IntegrationEnvelope(triggerId=CRON, sourceSystem=ERP,
  │                          entityType=ORDER, operation=CREATE)
  │     ↓
  │     IdempotencyFilter  (Redis – skip already-seen correlationIds)
  │     ↓
  │     XsltRoutingProcessor  →  routing-decision.xsl
  │     ↓
  │     routingSlip = validate-order,transform-to-wms,deliver-kafka
  │     ↓
  │     RoutingSlipExecutor  (step-by-step Camel dynamicRouter)
  │     ↓
  │     WatermarkRepository.updateWatermark(wmKey, recordId)
  │
  └─ idle until next tick
```

### Chainable XSLT routing

`routing-decision.xsl` extracts each source/entity routing logic into a **named template**
so the CRON branch and the HTTP branch share the same step list — changing the template
updates both triggers at once.

```xml
<!-- Named template owned by ERP / ORDER logic -->
<xsl:template name="erp-order-steps">
  <xsl:param name="execMode" as="xs:string" select="'TRANSIENT'"/>
  <routingSlip>validate-order,transform-to-wms,deliver-kafka</routingSlip>
  <executionMode><xsl:value-of select="$execMode"/></executionMode>
  <destination>WMS_KAFKA_TOPIC</destination>
  <slaClass>STANDARD</slaClass>
  <validationResult>APPROVED</validationResult>
  <rejectionReason/>
</xsl:template>

<!-- HTTP branch: ERP ORDER CREATE → DURABLE -->
<xsl:when test="$src='ERP' and $ent='ORDER' and $op='CREATE'">
  <xsl:call-template name="erp-order-steps">
    <xsl:with-param name="execMode" select="'DURABLE'"/>
  </xsl:call-template>
</xsl:when>

<!-- CRON branch: same step chain, BATCH execution mode -->
<xsl:when test="$trig='CRON' and $src='ERP' and $ent='ORDER'
                and ($op='CREATE' or $op='UPDATE')">
  <xsl:call-template name="erp-order-steps">
    <xsl:with-param name="execMode" select="'BATCH'"/>
  </xsl:call-template>
</xsl:when>

<!-- CRON fallback: unknown type → stage to BATCH_DB for review -->
<xsl:when test="$trig='CRON'">
  <xsl:call-template name="cron-default-steps"/>
</xsl:when>
```

**Rule for adding a new request type:**

1. Create (or activate) a row in the **Admin UI → Cron Config** page.
2. In `routing-decision.xsl`, add a named template for the new source/entity
   combination (or reuse an existing one).
3. Add an `<xsl:when>` in the CRON block that matches the new triple and calls
   the template with `execMode='BATCH'`.
4. If an HTTP rule already exists for the same source/entity, refactor it to
   call the same template with `execMode='DURABLE'` or `'TRANSIENT'`.

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

### Cron request-type configuration

```bash
# List all request types (active and disabled)
curl -s http://localhost:8080/admin/cron-types | python3 -m json.tool

# Create a new request type
curl -X POST http://localhost:8080/admin/cron-types \
  -H "Content-Type: application/json" \
  -d '{
    "name":         "ERP Invoice Sync",
    "sourceSystem": "ERP",
    "entityType":   "INVOICE",
    "operation":    "CREATE",
    "notes":        "Hourly sync of new invoices from ERP"
  }'

# Update name / notes (does not affect active state)
curl -X PUT http://localhost:8080/admin/cron-types/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"ERP Order Sync","sourceSystem":"ERP","entityType":"ORDER","operation":"CREATE"}'

# Toggle active ↔ disabled (records who made the change)
curl -X PUT "http://localhost:8080/admin/cron-types/1/toggle?by=jane.doe"

# Delete a request type permanently
curl -X DELETE http://localhost:8080/admin/cron-types/1
```

The `disabledAt` and `disabledBy` fields are set automatically on disable and
cleared on re-enable. Changes take effect on the **next scheduled tick** — no
restart required.

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
| `framework-cron` | Quartz scheduler (default hourly), `CronRequestType` DB config, watermark tracking, ERP adapter |
| `framework-delivery` | REST / Kafka / DB / hold queue / retry delivery steps |
| `framework-ui` | React dashboard (Vite + Recharts) — request log table, charts, XSLT admin, Cron Config admin |

---

## XSLT Routing Rules

Logic lives in named templates inside `routing-decision.xsl`; the CRON branch calls
the same templates as the HTTP branch, just with `execMode=BATCH`.

| Trigger | Source | Entity | Operation | Named template | Steps | Exec mode |
|---------|--------|--------|-----------|----------------|-------|-----------|
| HTTP | `CRM` | `USER` | `CREATE`, `MODIFY` | `crm-user-steps` | enrich-user → validate-user → transform-to-iam → deliver-rest | TRANSIENT |
| HTTP | `CRM` | `USER` | `DELETE` | *(inline)* | dead-letter (rejected) | TRANSIENT |
| HTTP | `ERP` | `ORDER` | `CREATE` | `erp-order-steps` | validate-order → transform-to-wms → deliver-kafka | DURABLE |
| HTTP | `ERP` | `ORDER` | `UPDATE` | `erp-order-steps` | validate-order → transform-to-wms → deliver-kafka | TRANSIENT |
| **CRON** | `ERP` | `ORDER` | `CREATE`, `UPDATE` | **`erp-order-steps`** | validate-order → transform-to-wms → deliver-kafka | **BATCH** |
| **CRON** | `CRM` | `USER` | any | **`crm-user-steps`** | enrich-user → validate-user → transform-to-iam → deliver-rest | **BATCH** |
| **CRON** | *(any)* | *(any)* | *(any)* | **`cron-default-steps`** | transform-generic → deliver-db | BATCH |
| *(any)* | *(any)* | *(any)* | *(any)* | *(none)* | dead-letter (rejected) | TRANSIENT |

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
