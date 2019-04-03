/*
 * Copyright (C) 2016 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.syndesis.connector.salesforce;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import io.syndesis.integration.component.proxy.ComponentDefinition;
import io.syndesis.integration.component.proxy.ComponentProxyComponent;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ServiceHelper;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.client.hotrod.configuration.SaslQop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SalesforceCachingComponentProxyComponent extends ComponentProxyComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(SalesforceCachingComponentProxyComponent.class);

    public SalesforceCachingComponentProxyComponent(String componentId, String componentScheme) {
        super(componentId, componentScheme);

    }

    @SuppressWarnings("PMD.ExcessiveMethodLength")
    @Override
    protected Endpoint createDelegateEndpoint(ComponentDefinition definition, String scheme, Map<String, String> options) throws Exception {
        final String uri = getCatalog().asEndpointUri(scheme, options, false);
        final AtomicReference<Endpoint> endpoint = new AtomicReference<>();
        final AtomicReference<Producer> producer = new AtomicReference<>();

        return new DefaultEndpoint() {
            @Override
            public void stop() throws Exception {
                super.stop();
                ServiceHelper.stopServices(endpoint.get());
            }
            @Override
            public void suspend() throws Exception {
                super.suspend();
                ServiceHelper.suspendService(endpoint.get());
            }
            @Override
            public void resume() throws Exception {
                super.resume();
                ServiceHelper.resumeService(endpoint.get());
            }

            @Override
            public String getEndpointUri() {
                return uri;
            }

            @Override
            public Producer createProducer() throws Exception {
                final ConcurrentMap<String, Data> cache = getCache();

                LOGGER.info("Using cache of type: {}", cache.getClass());

                return new DefaultProducer(this) {
                    @Override
                    public void stop() throws Exception {
                        super.stop();
                        ServiceHelper.stopService(producer.get());
                    }

                    @Override
                    public void suspend() throws Exception {
                        super.suspend();
                        ServiceHelper.suspendService(producer.get());
                    }

                    @Override
                    public void resume() throws Exception {
                        super.resume();
                        ServiceHelper.resumeService(producer.get());
                    }

                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String key = exchange.getIn().getMandatoryBody(String.class);
                        Data val = cache.get(key);

                        if (val == null) {
                            LOGGER.info("Lookup {}", key);

                            if (endpoint.get() == null) {
                                endpoint.set(SalesforceCachingComponentProxyComponent.this.getCamelContext().getEndpoint(uri));
                            }
                            if (producer.get() == null) {
                                producer.set(endpoint.get().createProducer());
                            }

                            //
                            // Start the service on the first non cached message
                            //
                            ServiceHelper.startService(endpoint.get());
                            ServiceHelper.startService(producer.get());

                            //
                            // let Salesforce producer process the exchange
                            //
                            producer.get().process(exchange);

                            //
                            // Don't cache failures as the new value may be defined later
                            //
                            if (!exchange.isFailed()) {
                                val = cache.computeIfAbsent(key, k -> new Data(exchange.getOut().getBody(byte[].class)));
                            }
                        } else {
                            LOGGER.info("Data for {} was cached", key);
                        }

                        if (val != null) {
                            //
                            // Set the body with the cached or retrieved value
                            //
                            exchange.getOut().setBody(new ByteArrayInputStream(val.payload));
                        }
                    }
                };
            }

            @Override
            public Consumer createConsumer(Processor processor) throws Exception {
                if (endpoint.get() == null) {
                    endpoint.set(getCamelContext().getEndpoint(uri));
                }

                return endpoint.get().createConsumer(processor);
            }

            @Override
            public boolean isSingleton() {
                return true;
            }
        };
    }

    private static ConcurrentMap<String, Data> getCache() {
        // "datagrid-service.datagrid-demo.svc.cluster.local"
        // 11222
        String host = System.getenv().get("DATAGRID_SERVICE_HOST");
        if (host != null) {
            String port = System.getenv().getOrDefault("DATAGRID_SERVICE_PORT", "11222");
            String name = System.getenv().getOrDefault("DATAGRID_CACHE_NAME", "camel-salesforce");

            Configuration config = new ConfigurationBuilder()
                .nearCache().mode(NearCacheMode.INVALIDATED)
                .addServer()
                .host(host)
                .port(Integer.parseInt(port))
                .security()
                .authentication()
                .enable()
                .username("admin")
                .password("admin")
                .realm("ApplicationRealm")
                .serverName("datagrid-service")
                .saslMechanism("DIGEST-MD5")
                .saslQop(SaslQop.AUTH)
                .build();

            return new RemoteCacheManager(config).getCache(name);
        } else {
            return new ConcurrentHashMap<>();
        }
    }

    private static class Data implements Serializable {
        final byte[] payload;

        public Data(byte[] payload) {
            this.payload = Arrays.copyOf(payload, payload.length);
        }
    }
}
