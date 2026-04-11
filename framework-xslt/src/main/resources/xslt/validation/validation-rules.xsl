<?xml version="1.0" encoding="UTF-8"?>
<!--
  VALIDATION RULES STYLESHEET
  ============================
  Standalone validation pass (optional – can also embed validation
  logic directly in routing-decision.xsl for simple cases).

  Use this stylesheet when validation is complex enough to warrant
  a separate pass before transformation (e.g. cross-field checks,
  regex validation, external schema conformance).

  Output: <validationResult>
            <outcome>APPROVED|REJECTED</outcome>
            <reason>...</reason>
          </validationResult>
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <xsl:variable name="p"   select="/envelope/payload"/>
  <xsl:variable name="ent" select="/envelope/entityType"/>
  <xsl:variable name="op"  select="/envelope/requestedOperation"/>

  <xsl:template match="/">
    <validationResult>
      <xsl:choose>

        <!-- USER must have a non-empty email in valid format -->
        <xsl:when test="$ent='USER' and not(matches(string($p/email), '^[^@]+@[^@]+\.[^@]+$'))">
          <outcome>REJECTED</outcome>
          <reason>User payload missing valid email address</reason>
        </xsl:when>

        <!-- ORDER must have a positive amount -->
        <xsl:when test="$ent='ORDER' and (not($p/amount) or xs:decimal($p/amount) &lt;= 0)">
          <outcome>REJECTED</outcome>
          <reason>Order amount must be a positive number</reason>
        </xsl:when>

        <!-- USER DELETE not allowed -->
        <xsl:when test="$ent='USER' and $op='DELETE'">
          <outcome>REJECTED</outcome>
          <reason>USER DELETE operations are not permitted via this integration</reason>
        </xsl:when>

        <!-- Default: approved -->
        <xsl:otherwise>
          <outcome>APPROVED</outcome>
          <reason/>
        </xsl:otherwise>

      </xsl:choose>
    </validationResult>
  </xsl:template>

</xsl:stylesheet>
