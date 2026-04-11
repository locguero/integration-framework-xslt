<?xml version="1.0" encoding="UTF-8"?>
<!--
  ERP ORDER -> WMS KAFKA EVENT TRANSFORMATION
  ============================================
  Maps ERP order payload to the WMS inbound Kafka event schema.

  Demonstrates XSLT handling of:
    - Nested object flattening (shippingAddress -> flat fields)
    - Numeric formatting   (amount -> formatted currency string)
    - Conditional priority (express shipping -> URGENT flag)
    - Date reformatting    (ISO -> WMS format)

  Input:  <envelope> XML
  Output: <wmsOrderEvent> XML
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    exclude-result-prefixes="xs">

  <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

  <xsl:variable name="p"   select="/envelope/payload"/>
  <xsl:variable name="cid" select="/envelope/correlationId"/>

  <xsl:template match="/">
    <wmsOrderEvent>
      <eventId><xsl:value-of select="$cid"/></eventId>
      <eventType>ORDER_INBOUND</eventType>

      <order>
        <wmsOrderId><xsl:value-of select="$p/orderId"/></wmsOrderId>
        <externalRef><xsl:value-of select="$p/id"/></externalRef>
        <status><xsl:value-of select="upper-case(string($p/status))"/></status>

        <!-- Conditional priority flag -->
        <priority>
          <xsl:choose>
            <xsl:when test="$p/shippingMethod='EXPRESS' or $p/shippingMethod='OVERNIGHT'">URGENT</xsl:when>
            <xsl:otherwise>NORMAL</xsl:otherwise>
          </xsl:choose>
        </priority>

        <!-- Numeric formatting -->
        <orderValue>
          <amount><xsl:value-of select="format-number(xs:decimal($p/amount), '0.00')"/></amount>
          <currency><xsl:value-of select="if ($p/currency != '') then $p/currency else 'USD'"/></currency>
        </orderValue>

        <!-- Flatten nested shipping address -->
        <deliveryAddress>
          <line1><xsl:value-of select="$p/shippingAddress/street"/></line1>
          <city><xsl:value-of select="$p/shippingAddress/city"/></city>
          <postCode><xsl:value-of select="$p/shippingAddress/postalCode"/></postCode>
          <country><xsl:value-of select="upper-case(string($p/shippingAddress/country))"/></country>
        </deliveryAddress>
      </order>

      <metadata>
        <sourceSystem><xsl:value-of select="/envelope/sourceSystem"/></sourceSystem>
        <processedAt><xsl:value-of select="current-dateTime()"/></processedAt>
      </metadata>
    </wmsOrderEvent>
  </xsl:template>

</xsl:stylesheet>
