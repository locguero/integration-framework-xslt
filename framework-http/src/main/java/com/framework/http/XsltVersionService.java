package com.framework.http;

import com.framework.xslt.XsltRoutingEngine;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class XsltVersionService {

    private static final Logger log = LoggerFactory.getLogger(XsltVersionService.class);

    private static final String[] XSLT_FILES = {
        "xslt/routing/routing-decision.xsl",
        "xslt/routing/fallback-routing.xsl",
        "xslt/transform/erp-order-to-wms.xsl",
        "xslt/transform/crm-user-to-iam.xsl",
        "xslt/transform/generic-batch.xsl",
        "xslt/validation/validation-rules.xsl"
    };

    private final XsltVersionRepository repo;
    private final XsltRoutingEngine routingEngine;

    public XsltVersionService(XsltVersionRepository repo, XsltRoutingEngine routingEngine) {
        this.repo = repo;
        this.routingEngine = routingEngine;
    }

    @PostConstruct
    @Transactional
    public void seedFromClasspath() {
        for (String path : XSLT_FILES) {
            String filename = path.substring(path.lastIndexOf('/') + 1);
            if (!repo.existsByFilename(filename)) {
                try {
                    String content = new ClassPathResource(path)
                            .getContentAsString(StandardCharsets.UTF_8);
                    XsltVersion v = new XsltVersion();
                    v.setFilename(filename);
                    v.setVersion(1);
                    v.setContent(content);
                    v.setActive(true);
                    v.setComment("Seeded from classpath on startup");
                    repo.save(v);
                    log.info("Seeded XSLT version 1 for {}", filename);
                } catch (IOException e) {
                    log.warn("Could not seed XSLT {}: {}", filename, e.getMessage());
                }
            }
        }
    }

    public List<XsltVersion> listAll() {
        return repo.findAll();
    }

    public List<XsltVersion> listByFilename(String filename) {
        return repo.findByFilenameOrderByVersionDesc(filename);
    }

    @Transactional
    public XsltVersion upload(String filename, String content, String comment) throws Exception {
        int nextVersion = repo.findTopByFilenameOrderByVersionDesc(filename)
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        repo.findByFilenameAndActive(filename, true).ifPresent(v -> {
            v.setActive(false);
            repo.save(v);
        });

        XsltVersion v = new XsltVersion();
        v.setFilename(filename);
        v.setVersion(nextVersion);
        v.setContent(content);
        v.setActive(true);
        v.setComment(comment);
        XsltVersion saved = repo.save(v);

        reloadEngine(filename, content);
        log.info("Uploaded and activated XSLT {} version {}", filename, nextVersion);
        return saved;
    }

    @Transactional
    public XsltVersion activate(String filename, int version) throws Exception {
        XsltVersion target = repo.findByFilenameAndVersion(filename, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No version " + version + " found for " + filename));

        repo.findByFilenameAndActive(filename, true).ifPresent(v -> {
            v.setActive(false);
            repo.save(v);
        });

        target.setActive(true);
        XsltVersion saved = repo.save(target);
        reloadEngine(filename, target.getContent());
        log.info("Activated XSLT {} version {}", filename, version);
        return saved;
    }

    private void reloadEngine(String filename, String content) throws Exception {
        if ("routing-decision.xsl".equals(filename)) {
            routingEngine.reloadRouting(content);
        } else if ("fallback-routing.xsl".equals(filename)) {
            routingEngine.reloadFallback(content);
        }
    }
}
