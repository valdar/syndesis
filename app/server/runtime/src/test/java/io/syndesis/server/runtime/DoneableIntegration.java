package io.syndesis.server.runtime;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;


public class DoneableIntegration extends CustomResourceDoneable<Integration> {
    public DoneableIntegration(Integration resource, Function<Integration,Integration> function) {
        super(resource, function);
    }
}
