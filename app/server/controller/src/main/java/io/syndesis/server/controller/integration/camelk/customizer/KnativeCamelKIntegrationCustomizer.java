package io.syndesis.server.controller.integration.camelk.customizer;

import io.syndesis.common.model.integration.IntegrationDeployment;
import io.syndesis.server.controller.integration.camelk.crd.Integration;
import io.syndesis.server.controller.integration.camelk.crd.IntegrationSpec;
import io.syndesis.server.controller.integration.camelk.crd.IntegrationTraitSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static io.syndesis.common.util.Optionals.asStream;

/**
 * Enables specific Knative traits if needed
 */
@Component
public class KnativeCamelKIntegrationCustomizer implements CamelKIntegrationCustomizer {

    private static final String KNATIVE_TRAIT = "knative";
    private static final String KNATIVE_SERVICE_TRAIT = "knative-service";
    private static final String DEPLOYER_TRAIT = "deployer";

    private static final String KNATIVE_SOURCE_CHANNEL_TAG = "knative-source-channel";
    private static final String KNATIVE_SINK_CHANNEL_TAG = "knative-sink-channel";

    private static final String HTTP_PASSIVE_TAG = "http-passive";

    @Override
    public Integration customize(IntegrationDeployment deployment, Integration integration) {
        integration = customizeChannels(deployment, integration);
        integration = customizeService(deployment, integration);
        return integration;
    }

    protected Integration customizeChannels(IntegrationDeployment deployment, Integration integration) {
        List<String> sourceChannels = deployment.getSpec().getFlows().stream()
            .flatMap(f -> f.getSteps().stream())
            .flatMap(s -> asStream(s.getAction().flatMap(a -> a.propertyTaggedWith(s.getConfiguredProperties(), KNATIVE_SOURCE_CHANNEL_TAG))))
            .collect(Collectors.toList());

        List<String> sinkChannels = deployment.getSpec().getFlows().stream()
            .flatMap(f -> f.getSteps().stream())
            .flatMap(s -> asStream(s.getAction().flatMap(a -> a.propertyTaggedWith(s.getConfiguredProperties(), KNATIVE_SINK_CHANNEL_TAG))))
            .collect(Collectors.toList());


        if (!sourceChannels.isEmpty() || !sinkChannels.isEmpty()) {
            String sources = sourceChannels.stream().collect(Collectors.joining(","));
            String sinks = sinkChannels.stream().collect(Collectors.joining(","));

            IntegrationSpec.Builder spec = new IntegrationSpec.Builder();
            if (integration.getSpec() != null) {
                spec = spec.from(integration.getSpec());
            }
            integration.setSpec(
                spec.putTraits(KNATIVE_TRAIT, new IntegrationTraitSpec.Builder()
                    .putConfiguration("enabled", "true")
                    .putConfiguration("sources", sources)
                    .putConfiguration("sinks", sinks)
                    .build()
                ).build()
            );
        }

        return integration;
    }

    protected Integration customizeService(IntegrationDeployment deployment, Integration integration) {
        boolean passiveHttpEndpoint = deployment.getSpec().getFlows().stream()
            .flatMap(f -> asStream(f.getSteps().stream().findFirst()))
            .flatMap(s -> asStream(s.getAction()))
            .anyMatch(a -> a.getTags().contains(HTTP_PASSIVE_TAG));

        if (passiveHttpEndpoint) {
            IntegrationSpec.Builder spec = new IntegrationSpec.Builder();
            if (integration.getSpec() != null) {
                spec = spec.from(integration.getSpec());
            }
            integration.setSpec(
                spec.putTraits(KNATIVE_SERVICE_TRAIT,
                    new IntegrationTraitSpec.Builder()
                        .putConfiguration("enabled", "true")
                        .putConfiguration("min-scale", "0")
                        .build()
                ).putTraits(DEPLOYER_TRAIT,
                    new IntegrationTraitSpec.Builder()
                        .putConfiguration("kind", KNATIVE_SERVICE_TRAIT)
                        .build()
                ).build()
            );
        }

        return integration;
    }
}
