package io.syndesis.server.runtime;

import io.fabric8.kubernetes.client.CustomResource;

public class Integration extends CustomResource {
    private IntegrationSpec spec;


    public IntegrationSpec getSpec() {
        return spec;
    }

    public void setSpec(IntegrationSpec spec) {
        this.spec = spec;
    }

    @Override
    public String toString() {
        return "Integration{" +
            "spec=" + spec +
            '}';
    }
}
