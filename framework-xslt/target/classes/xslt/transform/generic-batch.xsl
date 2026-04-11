<?xml version="1.0" encoding="UTF-8"?>
<!--
  GENERIC BATCH TRANSFORMATION
  =============================
  Transforms any generic CRON-polled record into the batch DB schema.
  Provides safe defaults for all optional fields.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <xsl:variable name="p"   select="/envelope/payload"/>
  <xsl:variable name="cid" select="/envelope/correlationId"/>

  <xsl:template match="/">
    <batchRecord>
      <correlationId><xsl:value-of select="$cid"/></correlationId>
      <externalId><xsl:value-of select="if ($p/id != '') then $p/id else $cid"/></externalId>
      <processingStatus>PENDING</processingStatus>
      <payload>
        <!-- Copy all payload child elements as-is -->
        <xsl:copy-of select="$p/*"/>
      </payload>
      <ingestedAt><xsl:value-of select="current-dateTime()"/></ingestedAt>
    </batchRecord>
  </xsl:template>

</xsl:stylesheet>
