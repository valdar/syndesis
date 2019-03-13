package io.syndesis.integration.runtime;

import org.apache.camel.CamelContext;

import java.io.InputStream;

/**
 * Used to directly provide the source for reading the integration
 */
@FunctionalInterface
public interface SourceProvider {

    InputStream getSource(CamelContext ctx) throws Exception;

}
