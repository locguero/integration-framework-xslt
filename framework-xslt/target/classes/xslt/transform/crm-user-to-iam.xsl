<?xml version="1.0" encoding="UTF-8"?>
<!--
  CRM USER -> IAM REST API TRANSFORMATION
  ========================================
  Maps the CRM user payload (inside <envelope><payload>) to the
  IAM REST API request body format.

  This stylesheet OWNS both field mapping AND conditional logic
  (e.g. role defaulting, status normalisation).
  In the old AtlasMap+DMN stack, these were split across two tools.
  Here they coexist naturally in XSLT.

  Input:  <envelope> from EnvelopeXmlConverter
  Output: <iamUser> XML (converted to JSON by delivery step via Jackson)

  Field mapping:
    payload/firstName  -> givenName
    payload/lastName   -> familyName
    payload/email      -> emailAddress
    payload/id         -> externalId
    payload/role       -> iamRole  (with conditional defaulting)
    requestedOperation -> actionType (CREATE -> PROVISION, MODIFY -> UPDATE)
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <xsl:variable name="payload" select="/envelope/payload"/>
  <xsl:variable name="op"      select="/envelope/requestedOperation"/>
  <xsl:variable name="cid"     select="/envelope/correlationId"/>

  <xsl:template match="/">
    <iamUser>
      <!-- Correlation passthrough for audit trail -->
      <correlationId><xsl:value-of select="$cid"/></correlationId>

      <!-- Operation mapping: CREATE->PROVISION, MODIFY->UPDATE -->
      <actionType>
        <xsl:choose>
          <xsl:when test="$op='CREATE'">PROVISION</xsl:when>
          <xsl:when test="$op='MODIFY'">UPDATE</xsl:when>
          <xsl:otherwise>UNKNOWN</xsl:otherwise>
        </xsl:choose>
      </actionType>

      <!-- Identity fields -->
      <externalId><xsl:value-of select="$payload/id"/></externalId>
      <givenName><xsl:value-of select="$payload/firstName"/></givenName>
      <familyName><xsl:value-of select="$payload/lastName"/></familyName>
      <emailAddress><xsl:value-of select="lower-case(string($payload/email))"/></emailAddress>

      <!-- Role mapping with default -->
      <iamRole>
        <xsl:choose>
          <xsl:when test="normalize-space($payload/role) != ''">
            <xsl:value-of select="upper-case(string($payload/role))"/>
          </xsl:when>
          <xsl:otherwise>STANDARD_USER</xsl:otherwise>
        </xsl:choose>
      </iamRole>

      <!-- Status normalisation -->
      <accountStatus>
        <xsl:choose>
          <xsl:when test="$payload/status='active' or $payload/status='ACTIVE'">ENABLED</xsl:when>
          <xsl:when test="$payload/status='inactive'">DISABLED</xsl:when>
          <xsl:otherwise>PENDING_ACTIVATION</xsl:otherwise>
        </xsl:choose>
      </accountStatus>

      <sourceSystem><xsl:value-of select="/envelope/sourceSystem"/></sourceSystem>
    </iamUser>
  </xsl:template>

</xsl:stylesheet>
