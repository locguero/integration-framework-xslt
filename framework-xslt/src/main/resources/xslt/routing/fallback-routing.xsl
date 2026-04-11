<?xml version="1.0" encoding="UTF-8"?>
<!--
  FALLBACK ROUTING STYLESHEET
  ============================
  Evaluated when the enrichment API circuit breaker is OPEN.
  enrichmentStatus = SKIPPED → route to a safe degraded path
  rather than failing the caller.

  PRIORITY SLA + circuit OPEN → durable hold queue + ops alert
  STANDARD SLA + circuit OPEN → async retry queue
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <xsl:variable name="enr" select="/envelope/enrichmentStatus" as="xs:string"/>
  <xsl:variable name="src" select="/envelope/sourceSystem"     as="xs:string"/>
  <xsl:variable name="ent" select="/envelope/entityType"       as="xs:string"/>

  <xsl:template match="/">
    <routingResult>
      <xsl:choose>

        <!-- PRIORITY source + circuit OPEN -->
        <xsl:when test="$enr='SKIPPED' and ($src='CRM' and $ent='USER')">
          <routingSlip>deliver-async-hold,alert-ops</routingSlip>
          <executionMode>DURABLE</executionMode>
          <destination>ASYNC_HOLD</destination>
          <slaClass>PRIORITY</slaClass>
          <validationResult>APPROVED</validationResult>
          <rejectionReason/>
        </xsl:when>

        <!-- Any other source + circuit OPEN -->
        <xsl:when test="$enr='SKIPPED'">
          <routingSlip>deliver-retry-queue</routingSlip>
          <executionMode>TRANSIENT</executionMode>
          <destination>RETRY_QUEUE</destination>
          <slaClass>STANDARD</slaClass>
          <validationResult>APPROVED</validationResult>
          <rejectionReason/>
        </xsl:when>

        <xsl:otherwise>
          <routingSlip>dead-letter</routingSlip>
          <executionMode>TRANSIENT</executionMode>
          <destination>DEAD_LETTER</destination>
          <slaClass>STANDARD</slaClass>
          <validationResult>REJECTED</validationResult>
          <rejectionReason>Fallback stylesheet invoked with unexpected state</rejectionReason>
        </xsl:otherwise>

      </xsl:choose>
    </routingResult>
  </xsl:template>

</xsl:stylesheet>
