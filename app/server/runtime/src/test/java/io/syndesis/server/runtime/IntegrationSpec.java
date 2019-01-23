package io.syndesis.server.runtime;

import io.fabric8.kubernetes.api.model.KubernetesResource;

import java.util.ArrayList;
import java.util.List;

public class IntegrationSpec implements KubernetesResource<Integration> {
    private List<SourceSpec> sources = new ArrayList<SourceSpec>();

    public List<SourceSpec> getSources() {
        return sources;
    }

    public void setSources(List<SourceSpec> sources) {
        this.sources = sources;
    }

    @Override
    public String toString() {
        return "IntegrationSpec{" +
            "sources=" + sources +
            '}';
    }

    public void addSourceSpec(SourceSpec source){
        sources.add(source);
    }
}
