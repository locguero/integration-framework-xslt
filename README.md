# Integration Framework – XSLT Edition

Apache Camel 4 + Saxon HE XSLT 2.0. Routing decisions and field transformations
unified under a single stylesheet-based approach. No Camunda DMN. No AtlasMap.

## Key Design Decision

Both **routing logic** (which steps to execute) and **field mapping** (firstName->givenName)
are implemented as XSLT 2.0 stylesheets processed by Saxon HE.

Trade-off: simpler tech stack at the cost of the BA drag-and-drop UI.
See `docs/ARCHITECTURE.md` for the full comparison.

## Quick Start

```bash
cd docker && docker-compose up -d redis kafka zookeeper
mvn clean package -DskipTests
java -jar framework-http/target/framework-http-1.0.0-SNAPSHOT.jar

curl -X POST http://localhost:8080/api/integration/ingest \
  -H "Content-Type: application/json"     \
  -H "X-Correlation-Id: test-001"         \
  -H "X-Source-System: CRM"               \
  -H "X-Entity-Type: USER"                \
  -H "X-Operation: CREATE"                \
  -d '{"id":"u-1","firstName":"Jane","lastName":"Doe","email":"jane@example.com","role":"admin"}'
```

## Modules

| Module               | Role                                              |
|----------------------|---------------------------------------------------|
| framework-core       | Envelope, Idempotency, Security, RoutingSlip      |
| framework-xslt       | Saxon engine, 5 stylesheets, Camel step routes    |
| framework-http       | HTTP trigger + EnrichmentStep                     |
| framework-kafka      | Kafka trigger, SEDA backpressure, DLT             |
| framework-cron       | Cron poll, watermark, ERP adapter                 |
| framework-delivery   | REST / Kafka / DB / DLT delivery steps            |

## Running XSLT Unit Tests (no Spring context needed)

```bash
mvn test -pl framework-xslt
```
