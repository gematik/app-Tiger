package de.gematik.test.tiger.proxy.wiremockUtils;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class WiremockProxyUrlTransformer extends ResponseDefinitionTransformer {

    public static final String EXTENSION_NAME = "tiger-proxy-url-transformer";
    private final Map<String, String> urlMap = new HashMap<>();

    @Override
    public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition, FileSource files,
        Parameters parameters) {
        try {
            URI requestUri = new URI(request.getAbsoluteUrl());
            for (Entry<String, String> entry : urlMap.entrySet()) {
                Optional<ResponseDefinition> definition = getResponseDefinition(responseDefinition, requestUri,
                    entry.getKey(), entry.getValue());
                if (definition.isPresent()) {
                    return definition.get();
                }
            }
            return responseDefinition;
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Optional<ResponseDefinition> getResponseDefinition(ResponseDefinition responseDefinition, URI requestUri,
        String mappingSource, String mappingTarget) {
        if (!mappingSource.equals(requestUri.getScheme() + "://" + requestUri.getHost())) {
            return Optional.empty();
        }

        String proxyUrl = mappingTarget;
        if (requestUri.getPath() != null) {
            proxyUrl += requestUri.getPath().substring(mappingSource.length());
        }
        if (requestUri.getRawQuery() != null) {
            proxyUrl += requestUri.getRawQuery();
        }

        log.info("Forwarding to '" + proxyUrl + "'");
        return Optional.ofNullable(ResponseDefinitionBuilder
            .like(responseDefinition)
            .proxiedFrom(proxyUrl)
            .build());
    }

    @Override
    public boolean applyGlobally() {
        return true;
    }

    @Override
    public String getName() {
        return EXTENSION_NAME;
    }
}
