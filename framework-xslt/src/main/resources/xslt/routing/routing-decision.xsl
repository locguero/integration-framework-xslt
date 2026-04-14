<?xml version="1.0" encoding="UTF-8"?>
<!--
  ROUTING DECISION STYLESHEET
  ============================
  Replaces the Camunda DMN ROUTING_SLIP_DECISION table.

  Input:  <envelope> XML (from EnvelopeXmlConverter)
  Output: <routingResult> XML

  Routing slip values are comma-separated step names that map
  to Camel route URIs via StepRegistry / application.yml.

  XSLT advantages over DMN for routing:
    - Full XPath 2.0 boolean expressions (and/or/not, matches(), etc.)
    - No GUI tooling dependency
    - Same syntax as the transformation stylesheets (one language to learn)
    - Can import shared XSL templates (xsl:import) for DRY logic

  ─── CHAINABLE NAMED TEMPLATES ───────────────────────────────────────────────

  Routing logic for each source/entity/operation combination is extracted into
  a named template (e.g. erp-order-steps, crm-user-steps).  Both the HTTP
  trigger branch AND the CRON trigger branch call these templates, passing an
  $execMode parameter so the execution mode can differ (DURABLE vs BATCH)
  without duplicating the step list.

  Flow for a CRON-triggered ERP ORDER CREATE:

    CronTriggerRoute
      │  reads active CronRequestType rows (cron_request_type table)
      │  builds IntegrationEnvelope(triggerId=CRON, sourceSystem=ERP,
      │                              entityType=ORDER, operation=CREATE)
      ▼
    XsltRoutingProcessor
      │  converts envelope → <envelope> XML
      │  applies routing-decision.xsl
      ▼
    routing-decision.xsl
      │  $trig = 'CRON'  →  outer CRON when fires
      │  $src  = 'ERP', $ent = 'ORDER', $op = 'CREATE'
      │  → calls named template  erp-order-steps($execMode='BATCH')
      ▼
    routingResult / routingSlip = validate-order,transform-to-wms,deliver-kafka
      │
      ▼
    RoutingSlipExecutor  (drives Camel dynamicRouter step by step)

  To add a new routing rule:
    1. Add a named template for the new source/entity combination
    2. Call it from the HTTP branch (inline when) with DURABLE or TRANSIENT
    3. Add a <xsl:when> in the CRON block calling the same template with BATCH
    4. Commit, push – CI pipeline runs XSLT unit tests automatically
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <!-- ─── Input field shortcuts ──────────────────────────────────── -->
  <xsl:variable name="src"  select="/envelope/sourceSystem"       as="xs:string"/>
  <xsl:variable name="ent"  select="/envelope/entityType"         as="xs:string"/>
  <xsl:variable name="op"   select="/envelope/requestedOperation" as="xs:string"/>
  <xsl:variable name="trig" select="/envelope/triggerId"          as="xs:string"/>
  <xsl:variable name="enr"  select="/envelope/enrichmentStatus"   as="xs:string"/>

  <!-- ═══════════════════════════════════════════════════════════════════════
       NAMED TEMPLATES — chainable step lists
       Each template owns the routing slip for one source/entity combination.
       Pass $execMode to let the caller control execution mode without copying
       the step list (DURABLE for HTTP primary path, BATCH for CRON polling).
       ═══════════════════════════════════════════════════════════════════════ -->

  <!--
    erp-order-steps
    ───────────────
    Called by:
      • HTTP branch  – ERP / ORDER / CREATE  (execMode=DURABLE)
      • HTTP branch  – ERP / ORDER / UPDATE  (execMode=TRANSIENT)
      • CRON branch  – ERP / ORDER / CREATE or UPDATE  (execMode=BATCH)

    Step chain: validate-order → transform-to-wms → deliver-kafka
  -->
  <xsl:template name="erp-order-steps">
    <xsl:param name="execMode" as="xs:string" select="'TRANSIENT'"/>
    <routingSlip>validate-order,transform-to-wms,deliver-kafka</routingSlip>
    <executionMode><xsl:value-of select="$execMode"/></executionMode>
    <destination>WMS_KAFKA_TOPIC</destination>
    <slaClass>STANDARD</slaClass>
    <validationResult>APPROVED</validationResult>
    <rejectionReason/>
  </xsl:template>

  <!--
    crm-user-steps
    ──────────────
    Called by:
      • HTTP branch  – CRM / USER / CREATE or MODIFY  (execMode=TRANSIENT)
      • CRON branch  – CRM / USER / *  (execMode=BATCH)  ← example extension

    Step chain: enrich-user → validate-user → transform-to-iam → deliver-rest
  -->
  <xsl:template name="crm-user-steps">
    <xsl:param name="execMode" as="xs:string" select="'TRANSIENT'"/>
    <routingSlip>enrich-user,validate-user,transform-to-iam,deliver-rest</routingSlip>
    <executionMode><xsl:value-of select="$execMode"/></executionMode>
    <destination>IAM_REST_API</destination>
    <slaClass>PRIORITY</slaClass>
    <validationResult>APPROVED</validationResult>
    <rejectionReason/>
  </xsl:template>

  <!--
    cron-default-steps
    ──────────────────
    Fallback when the CRON trigger carries a sourceSystem/entityType/operation
    combination that has no dedicated named template above.
    Stages the raw record in the batch_record table for manual review.
  -->
  <xsl:template name="cron-default-steps">
    <routingSlip>transform-generic,deliver-db</routingSlip>
    <executionMode>BATCH</executionMode>
    <destination>BATCH_DB</destination>
    <slaClass>BATCH</slaClass>
    <validationResult>APPROVED</validationResult>
    <rejectionReason/>
  </xsl:template>


  <!-- ═══════════════════════════════════════════════════════════════════════
       MAIN ROUTING DECISION
       Rules are evaluated top-to-bottom; the first match wins.
       ═══════════════════════════════════════════════════════════════════════ -->

  <xsl:template match="/">
    <routingResult>
      <xsl:choose>

        <!-- ── CRON / BATCH trigger ─────────────────────────────────────────
             Evaluated FIRST so the trigger type always takes precedence.

             Each active CronRequestType row in the cron_request_type table
             contributes a distinct (sourceSystem, entityType, operation) triple
             to the envelopes produced by CronTriggerRoute.  We match on those
             triples here and delegate to the SAME named templates used by the
             HTTP branch — execution mode is set to BATCH instead of DURABLE.

             Adding a new cron request type:
               1. Activate (or create) a row in the admin UI → Cron Config page.
               2. Add an <xsl:when> below that matches the new triple and
                  calls the appropriate named template with execMode='BATCH'.
               3. If no named template exists yet, create one (see above).
             ──────────────────────────────────────────────────────────────── -->

        <!-- CRON: ERP ORDER CREATE or UPDATE
             ↳ reuses erp-order-steps with BATCH execution mode              -->
        <xsl:when test="$trig='CRON' and $src='ERP' and $ent='ORDER'
                        and ($op='CREATE' or $op='UPDATE')">
          <xsl:call-template name="erp-order-steps">
            <xsl:with-param name="execMode" select="'BATCH'"/>
          </xsl:call-template>
        </xsl:when>

        <!-- CRON: CRM USER (any operation)
             ↳ reuses crm-user-steps with BATCH execution mode
             ↳ example of how to extend CRON to a second request type        -->
        <xsl:when test="$trig='CRON' and $src='CRM' and $ent='USER'">
          <xsl:call-template name="crm-user-steps">
            <xsl:with-param name="execMode" select="'BATCH'"/>
          </xsl:call-template>
        </xsl:when>

        <!-- CRON: default fallback
             ↳ source/entity/op not matched above → stage to BATCH_DB        -->
        <xsl:when test="$trig='CRON'">
          <xsl:call-template name="cron-default-steps"/>
        </xsl:when>

        <!-- ── HTTP rules ────────────────────────────────────────────────── -->

        <!-- CRM USER CREATE or MODIFY -->
        <xsl:when test="$src='CRM' and $ent='USER' and ($op='CREATE' or $op='MODIFY')">
          <xsl:call-template name="crm-user-steps">
            <xsl:with-param name="execMode" select="'TRANSIENT'"/>
          </xsl:call-template>
        </xsl:when>

        <!-- CRM USER DELETE → reject -->
        <xsl:when test="$src='CRM' and $ent='USER' and $op='DELETE'">
          <routingSlip>dead-letter</routingSlip>
          <executionMode>TRANSIENT</executionMode>
          <destination>DEAD_LETTER</destination>
          <slaClass>STANDARD</slaClass>
          <validationResult>REJECTED</validationResult>
          <rejectionReason>USER DELETE operations are not permitted via this integration</rejectionReason>
        </xsl:when>

        <!-- ERP ORDER UPDATE → transient -->
        <xsl:when test="$src='ERP' and $ent='ORDER' and $op='UPDATE'">
          <xsl:call-template name="erp-order-steps">
            <xsl:with-param name="execMode" select="'TRANSIENT'"/>
          </xsl:call-template>
        </xsl:when>

        <!-- ERP ORDER CREATE → durable -->
        <xsl:when test="$src='ERP' and $ent='ORDER' and $op='CREATE'">
          <xsl:call-template name="erp-order-steps">
            <xsl:with-param name="execMode" select="'DURABLE'"/>
          </xsl:call-template>
        </xsl:when>

        <!-- Default: dead-letter -->
        <xsl:otherwise>
          <routingSlip>dead-letter</routingSlip>
          <executionMode>TRANSIENT</executionMode>
          <destination>DEAD_LETTER</destination>
          <slaClass>STANDARD</slaClass>
          <validationResult>REJECTED</validationResult>
          <rejectionReason>No routing rule matched for this envelope</rejectionReason>
        </xsl:otherwise>

      </xsl:choose>
    </routingResult>
  </xsl:template>

</xsl:stylesheet>
