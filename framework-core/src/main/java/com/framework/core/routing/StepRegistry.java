package com.framework.core.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps logical step names (emitted by XSLT routing result) to Camel route URIs.
 * Externalised to application.yml – new steps require no code changes.
 *
 * Example:
 *   framework.steps.enrich-user=direct:step-enrich-user
 *   framework.steps.transform-to-iam=direct:step-transform-iam
 */
@Component
@ConfigurationProperties(prefix = "framework")
public class StepRegistry {

    private Map<String, String> steps = new HashMap<>();
    public Map<String, String> getSteps() { return steps; }
    public void setSteps(Map<String, String> steps) { this.steps = steps; }

    public String resolveUri(String stepName) {
        String uri = steps.get(stepName);
        if (uri == null) throw new UnknownStepException("No route for step: " + stepName);
        return uri;
    }

    public static class UnknownStepException extends RuntimeException {
        public UnknownStepException(String msg) { super(msg); }
    }
}
