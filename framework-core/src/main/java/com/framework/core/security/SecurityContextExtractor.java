package com.framework.core.security;

import com.framework.core.model.IntegrationEnvelope;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.MDC;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Extracts JWT claims and attaches them to the Camel exchange.
 * JWT is forwarded as-is to enrichment APIs and delivery endpoints.
 * MDC is populated for structured log correlation.
 */
@Component
public class SecurityContextExtractor implements Processor {

    public static final String PROP_JWT_RAW   = "JWT_RAW";
    public static final String PROP_USER_ID   = "USER_ID";
    public static final String PROP_TENANT_ID = "TENANT_ID";

    private final JwtDecoder jwtDecoder;
    public SecurityContextExtractor(JwtDecoder jwtDecoder) { this.jwtDecoder = jwtDecoder; }

    @Override
    public void process(Exchange exchange) {
        IntegrationEnvelope env = exchange.getIn().getBody(IntegrationEnvelope.class);
        String auth = env.headers().get("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return;
        try {
            Jwt jwt = jwtDecoder.decode(auth.substring(7));
            exchange.setProperty(PROP_JWT_RAW,   auth.substring(7));
            exchange.setProperty(PROP_USER_ID,   jwt.getSubject());
            exchange.setProperty(PROP_TENANT_ID, jwt.getClaimAsString("tenant_id"));
            MDC.put("userId",        jwt.getSubject());
            MDC.put("tenantId",      jwt.getClaimAsString("tenant_id"));
            MDC.put("correlationId", env.correlationId());
        } catch (Exception e) {
            // Non-fatal – continue without user context
        }
    }
}
