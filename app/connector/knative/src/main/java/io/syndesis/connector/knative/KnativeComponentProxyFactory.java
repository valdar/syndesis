package io.syndesis.connector.knative;

import io.syndesis.integration.component.proxy.ComponentDefinition;
import io.syndesis.integration.component.proxy.ComponentProxyComponent;
import io.syndesis.integration.component.proxy.ComponentProxyFactory;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


public final class KnativeComponentProxyFactory implements ComponentProxyFactory {
    @Override
    public ComponentProxyComponent newInstance(String componentId, String componentScheme) {
        return new ComponentProxyComponent(componentId, componentScheme) {
            @Override
            @SuppressWarnings("PMD.SignatureDeclareThrowsException")
            protected Optional<Component> createDelegateComponent(ComponentDefinition definition, Map<String, Object> options) throws Exception {
                // No specific component options
                return Optional.empty();
            }

            @Override
            protected CamelCatalog createCatalog() {
                CamelCatalog catalog = super.createCatalog();
                String jsonSchema;
                try (InputStream schemaStream = getClass().getResourceAsStream("/org/apache/camel/component/knative/knative.json")) {
                    jsonSchema = IOUtils.toString(schemaStream, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
                catalog.addComponent("knative", "org.apache.camel.component.knative.KnativeComponent", jsonSchema);
                return catalog;
            }

            @Override
            @SuppressWarnings("PMD.SignatureDeclareThrowsException")
            protected Endpoint createDelegateEndpoint(ComponentDefinition definition, String scheme, Map<String, String> options) throws Exception {
                return getCamelContext().getEndpoint(computeKnativeUri(scheme, options));
            }
        };
    }

    public static String computeKnativeUri(String scheme, Map<String, String> options) throws Exception {
        Map<String, Object> uriOptions = new HashMap<>(options);
        String type = (String) uriOptions.remove("type");
        String name = (String) uriOptions.remove("name");
        String uri = scheme + "://" + type + "/" + name;
        if (!uriOptions.isEmpty()) {
            uri = URISupport.appendParametersToURI(uri, uriOptions);
        }
        return uri;
    }
}
