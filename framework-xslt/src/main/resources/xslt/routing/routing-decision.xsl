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

  To add a new routing rule:
    1. Add an <xsl:when> block in the <xsl:choose> below
    2. Define the step list in <routingSlip>
    3. Commit, push – CI pipeline runs XSLT unit tests automatically
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

  <xsl:template match="/">
    <routingResult>
      <xsl:choose>

        <!-- ── Rule: CRON / BATCH trigger (evaluated first — takes priority) ── -->
        <xsl:when test="$trig='CRON'">
          <routingSlip>transform-generic,deliver-db</routingSlip>
          <executionMode>TRANSIENT</executionMode>
          <destination>BATCH_DB</destination>
          <slaClass>BATCH</slaClass>
          <validationResult>APPROVED</validationResult>
          <rejectionReason/>
        </xsl:when>

        <!-- ── Rule: CRM USER CREATE or MODIFY ──────────────────── -->
        <xsl:when test="$src='CRM' and $ent='USER' and ($op='CREATE' or $op='MODIFY')">
          <routingSlip>enrich-user,validate-user,transform-to-iam,deliver-rest</routingSlip>
          <executionMode>TRANSIENT</executionMode>
          <destination>IAM_REST_API</destination>
          <slaClass>PRIORITY</slaClass>
          <validationResult>APPROVED</validationResult>
          <rejectionReason/>
        </xsl:when>

        <!-- ── Rule: CRM USER DELETE → reject ───────────────────── -->
        <xsl:when test="$src='CRM' and $ent='USER' and $op='DELETE'">
          <routingSlip>dead-letter</routingSlip>
          <executionMode>TRANSIENT</executionMode>
          <destination>DEAD_LETTER</destination>
          <slaClass>STANDARD</slaClass>
          <validationResult>REJECTED</validationResult>
          <rejectionReason>USER DELETE operations are not permitted via this integration</rejectionReason>
        </xsl:when>

        <!-- ── Rule: ERP ORDER UPDATE → transient ───────────────── -->
        <xsl:when test="$src='ERP' and $ent='ORDER' and $op='UPDATE'">
          <routingSlip>validate-order,transform-to-wms,deliver-kafka</routingSlip>
          <executionMode>TRANSIENT</executionMode>
          <destination>WMS_KAFKA_TOPIC</destination>
          <slaClass>STANDARD</slaClass>
          <validationResult>APPROVED</validationResult>
          <rejectionReason/>
        </xsl:when>

        <!-- ── Rule: ERP ORDER CREATE → durable (long-running) ──── -->
        <xsl:when test="$src='ERP' and $ent='ORDER' and $op='CREATE'">
          <routingSlip>validate-order,transform-to-wms,deliver-kafka</routingSlip>
          <executionMode>DURABLE</executionMode>
          <destination>WMS_KAFKA_TOPIC</destination>
          <slaClass>STANDARD</slaClass>
          <validationResult>APPROVED</validationResult>
          <rejectionReason/>
        </xsl:when>

        <!-- ── Default: dead-letter ──────────────────────────────── -->
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
