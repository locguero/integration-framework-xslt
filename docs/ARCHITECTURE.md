# Integration Framework XSLT Edition – Architecture

## Technology Comparison: Previous (DMN + AtlasMap) vs This (XSLT)

| Concern              | Previous Stack              | XSLT Edition              |
|----------------------|-----------------------------|---------------------------|
| Routing decisions    | Camunda DMN table           | routing-decision.xsl      |
| Field transformation | AtlasMap .adm (drag-drop)   | crm-user-to-iam.xsl etc.  |
| Conditional logic    | DMN FEEL expressions        | xsl:choose / XPath 2.0    |
| Fallback routing     | fallback-routing-decision.dmn | fallback-routing.xsl    |
| Validation           | DMN validation table        | validation-rules.xsl      |
| XSLT engine          | N/A                         | Saxon HE 12.x (XSLT 2.0) |
| BA UI                | AtlasMap drag-and-drop      | **None** (IDE only)       |
| Dependencies removed | –                           | Camunda DMN, AtlasMap     |

## What XSLT Gains vs DMN+AtlasMap

- **One language** for both routing logic and field mapping
- **No GUI infrastructure** to deploy or maintain (AtlasMap service removed)
- **XSLT 2.0 power**: XPath functions, regex, grouping, format-date(), xsl:import
- **Testable without Spring**: unit tests run Saxon directly, sub-millisecond
- **Diffable**: .xsl files are plain XML, Git diffs are human-readable

## What Is Lost

- **BA drag-and-drop UI**: mapping changes now require engineers + Git PR
- **DMN decision table view**: business rules are in XSL code, not a table grid
- **AtlasMap schema detection**: engineers must know source/target schemas

## XSLT Stylesheet Inventory

| Stylesheet                           | Replaces                        |
|--------------------------------------|---------------------------------|
| xslt/routing/routing-decision.xsl   | DMN ROUTING_SLIP_DECISION table |
| xslt/routing/fallback-routing.xsl   | DMN FALLBACK_ROUTING_DECISION   |
| xslt/validation/validation-rules.xsl| DMN VALIDATION_DECISION table   |
| xslt/transform/crm-user-to-iam.xsl  | AtlasMap crm-user-to-iam.adm    |
| xslt/transform/erp-order-to-wms.xsl | AtlasMap erp-order-to-wms.adm   |
| xslt/transform/generic-batch.xsl    | AtlasMap generic-batch.adm      |

## Data Flow

```
HTTP / Kafka / Cron
       |
       v
 IntegrationEnvelope (canonical model)
       |
       v
 IdempotencyFilter  (Redis SETNX)
       |
       v
 SecurityContextExtractor (JWT -> MDC)
       |
       v
 XsltRoutingProcessor
   └─> EnvelopeXmlConverter: envelope -> XML
   └─> Saxon: routing-decision.xsl OR fallback-routing.xsl
   └─> Parse <routingResult> -> RoutingResult record
       |
       v
 Camel dynamicRouter (RoutingSlipExecutor)
   |
   ├─> step-enrich-user  (Resilience4j CB -> SKIPPED -> fallback XSL)
   ├─> step-validate-*   (hook for runtime checks; validation in routing XSL)
   ├─> step-transform-*
   |     └─> XsltTransformProcessor
   |           └─> Saxon: crm-user-to-iam.xsl / erp-order-to-wms.xsl etc.
   |           └─> Sets transformed XML as message body
   └─> step-deliver-*    (REST / Kafka / DB / DLT)
```

## Adding a New Integration Route

1. Add `<xsl:when>` block to `routing-decision.xsl`
2. Add corresponding test in `RoutingDecisionXsltTest`
3. Create `xslt/transform/new-transform.xsl`
4. Register in `XsltTransformEngine.STYLESHEETS`
5. Add `XsltStepRoutes` route for `direct:step-transform-new`
6. Register in `application-core.yml` step registry
7. CI runs XSLT unit tests automatically on PR
