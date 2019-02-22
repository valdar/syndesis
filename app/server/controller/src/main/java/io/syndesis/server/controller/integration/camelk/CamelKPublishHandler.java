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
package io.syndesis.server.controller.integration.camelk;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.syndesis.common.model.Dependency;
import io.syndesis.common.model.Kind;
import io.syndesis.common.model.ResourceIdentifier;
import io.syndesis.common.model.integration.Flow;
import io.syndesis.common.model.integration.Integration;
import io.syndesis.common.model.integration.IntegrationDeployment;
import io.syndesis.common.model.integration.IntegrationDeploymentState;
import io.syndesis.common.model.integration.Step;
import io.syndesis.common.model.integration.StepKind;
import io.syndesis.common.model.openapi.OpenApi;
import io.syndesis.common.util.Json;
import io.syndesis.common.util.Labels;
import io.syndesis.common.util.Names;
import io.syndesis.integration.api.IntegrationProjectGenerator;
import io.syndesis.integration.api.IntegrationResourceManager;
import io.syndesis.integration.project.generator.mvn.MavenGav;
import io.syndesis.server.controller.StateChangeHandler;
import io.syndesis.server.controller.StateUpdate;
import io.syndesis.server.controller.integration.IntegrationPublishValidator;
import io.syndesis.server.controller.integration.camelk.crd.ConfigurationSpec;
import io.syndesis.server.controller.integration.camelk.crd.DoneableIntegration;
import io.syndesis.server.controller.integration.camelk.crd.ImmutableIntegrationSpec;
import io.syndesis.server.controller.integration.camelk.crd.IntegrationList;
import io.syndesis.server.controller.integration.camelk.crd.IntegrationSpec;
import io.syndesis.server.controller.integration.camelk.crd.IntegrationTraitSpec;
import io.syndesis.server.controller.integration.camelk.crd.ResourceSpec;
import io.syndesis.server.controller.integration.camelk.crd.SourceSpec;
import io.syndesis.server.dao.IntegrationDao;
import io.syndesis.server.dao.IntegrationDeploymentDao;
import io.syndesis.server.endpoint.v1.VersionService;
import io.syndesis.server.openshift.OpenShiftService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Qualifier("camel-k")
@ConditionalOnProperty(value = "controllers.integration", havingValue = "camel-k")
public class CamelKPublishHandler extends BaseCamelKHandler implements StateChangeHandler {

    private final IntegrationResourceManager resourceManager;
    private final IntegrationProjectGenerator projectGenerator;
    private final VersionService versionService;

    private boolean compress;

    public CamelKPublishHandler(
            OpenShiftService openShiftService,
            IntegrationDao iDao,
            IntegrationDeploymentDao idDao,
            IntegrationProjectGenerator projectGenerator,
            IntegrationPublishValidator validator,
            IntegrationResourceManager resourceManager,
            VersionService versionService) {
        super(openShiftService, iDao, idDao, validator);
        this.projectGenerator = projectGenerator;
        this.resourceManager = resourceManager;
        this.versionService = versionService;

        // this should be taken from a configuration
        this.compress = false;
    }

    @Override
    public Set<IntegrationDeploymentState> getTriggerStates() {
        return Collections.singleton(IntegrationDeploymentState.Published);
    }

    @Override
    public StateUpdate execute(IntegrationDeployment integrationDeployment) {
        StateUpdate updateViaValidation = getValidator().validate(integrationDeployment);
        if (updateViaValidation != null) {
            return updateViaValidation;
        }

        //
        // Validation
        //

        if (!integrationDeployment.getUserId().isPresent()) {
            throw new IllegalStateException("Couldn't find the user of the integration");
        }
        if (!integrationDeployment.getIntegrationId().isPresent()) {
            throw new IllegalStateException("IntegrationDeployment should have an integrationId");
        }

        CustomResourceDefinition integrationCRD = getCustomResourceDefinition();

        if (isBuildFailed(integrationDeployment, integrationCRD)) {
            logInfo(integrationDeployment, "Build Failed");
            return new StateUpdate(IntegrationDeploymentState.Error, Collections.emptyMap(), "Build Failed");
        }
        if (isBuildStarted(integrationDeployment, integrationCRD)) {
            logInfo(integrationDeployment, "Build Started");
            return new StateUpdate(IntegrationDeploymentState.Pending, Collections.emptyMap(), "Build Started");
        }
        if (isRunning(integrationDeployment, integrationCRD)) {
            logInfo(integrationDeployment, "Running");
            return new StateUpdate(IntegrationDeploymentState.Published, Collections.emptyMap(), "Running");
        }

        return createIntegration(integrationDeployment, integrationCRD);
    }

    @SuppressWarnings({"unchecked"})
    protected StateUpdate createIntegration(IntegrationDeployment integrationDeployment, CustomResourceDefinition integrationCRD) {
        Map<String, String> stepsDone = new HashMap<>(integrationDeployment.getStepsDone());

        logInfo(integrationDeployment,"Creating Camel-K resource");

        prepareDeployment(integrationDeployment);

        io.syndesis.server.controller.integration.camelk.crd.Integration camelkIntegration = createIntegrationCR(integrationDeployment);
        Secret camelkSecrets = createIntegrationSecret(integrationDeployment);

        getOpenShiftService().createOrReplaceSecret(camelkSecrets);
        getOpenShiftService().createOrReplaceCR(integrationCRD,
            io.syndesis.server.controller.integration.camelk.crd.Integration.class,
            IntegrationList.class,
            DoneableIntegration.class,
            camelkIntegration);
        stepsDone.put("deploy", "camel-k");
        logInfo(integrationDeployment,"Camel-K resource created");

        return new StateUpdate(IntegrationDeploymentState.Pending, stepsDone);
    }

    protected Secret createIntegrationSecret(IntegrationDeployment integrationDeployment) {
        final Integration integration = integrationDeployment.getSpec();

        Properties applicationProperties = projectGenerator.generateApplicationProperties(integration);

        //TODO: maybe add owner reference
        Secret secret = new SecretBuilder()
            .withNewMetadata()
                .withName(Names.sanitize(integration.getId().get()))
            .endMetadata()
            .addToStringData("application.properties", CamelKSupport.propsToString(applicationProperties))
            .build();

        return secret;
    }

    protected io.syndesis.server.controller.integration.camelk.crd.Integration createIntegrationCR(IntegrationDeployment integrationDeployment) {
        final Integration integration = integrationDeployment.getSpec();

        String username = integrationDeployment.getUserId().get();
        String integrationId = integrationDeployment.getIntegrationId().get();
        String version = Integer.toString(integrationDeployment.getVersion());

        io.syndesis.server.controller.integration.camelk.crd.Integration result = new io.syndesis.server.controller.integration.camelk.crd.Integration();
        //add CR metadata
        result.getMetadata().setName(Names.sanitize(integrationId));
//        result.getMetadata().setResourceVersion(String.valueOf(integrationDeployment.getVersion()));
        result.getMetadata().setLabels(new HashMap<>());
        result.getMetadata().getLabels().put(OpenShiftService.INTEGRATION_ID_LABEL, Labels.validate(integrationId));
        result.getMetadata().getLabels().put(OpenShiftService.DEPLOYMENT_VERSION_LABEL, version);
        result.getMetadata().getLabels().put(OpenShiftService.USERNAME_LABEL, Labels.sanitize(username));
        result.getMetadata().setAnnotations(new HashMap<>());
        result.getMetadata().getAnnotations().put(OpenShiftService.INTEGRATION_NAME_ANNOTATION, integration.getName());
        result.getMetadata().getAnnotations().put(OpenShiftService.INTEGRATION_ID_LABEL, integrationId);
        result.getMetadata().getAnnotations().put(OpenShiftService.DEPLOYMENT_VERSION_LABEL, version);

        ImmutableIntegrationSpec.Builder integrationSpecBuilder = new IntegrationSpec.Builder();

        //add customizers
        integrationSpecBuilder.addConfiguration(new ConfigurationSpec.Builder()
            .type("property")
            .value("camel.k.customizer=metadata,logging")
            .build());
        integrationSpecBuilder.addConfiguration(new ConfigurationSpec.Builder()
            .type("secret")
            .value(Names.sanitize(integration.getId().get()))
            .build());
        integrationSpecBuilder.putTraits(
            "camel",
            new IntegrationTraitSpec.Builder()
                //TODO: this should be provided by the VersionService
                .putConfiguration("version", "2.21.0.fuse-730049")
                .build()
        );

        //add dependencies
        getDependencies(integration).forEach( gav -> integrationSpecBuilder.addDependencies("mvn:"+gav.getId()));
        integrationSpecBuilder.addDependencies("mvn:io.syndesis.integration:integration-runtime-camelk:"+versionService.getVersion());

        try {
            addMappingRules(integration, integrationSpecBuilder);
            addOpenAPIDefinition(integration, integrationSpecBuilder);
            addIntegrationSource(integration, integrationSpecBuilder);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }

        result.setSpec(integrationSpecBuilder.build());
        return result;
    }

    private Set<MavenGav> getDependencies(Integration integration){
        return resourceManager.collectDependencies(integration).stream()
            .filter(Dependency::isMaven)
            .map(Dependency::getId)
            .map(MavenGav::new)
//            .filter(ProjectGeneratorHelper::filterDefaultDependencies)
            .collect(Collectors.toCollection(TreeSet::new));
    }

    private String extractIntegrationJson(Integration fullIntegration) {
        Integration integration = resourceManager.sanitize(fullIntegration);
        ObjectWriter writer = Json.writer();
        try {

            return new String(writer.with(writer.getConfig().getDefaultPrettyPrinter()).writeValueAsBytes(integration), UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot convert integration " + integration.getName() + " to JSON: " + e,e);
        }
    }

    private void prepareDeployment(IntegrationDeployment integrationDeployment) {
        setVersion(integrationDeployment);
//        deactivatePreviousDeployments(integrationDeployment);
    }

    // ************************************
    //
    // Add resources to Integration Spec
    //
    // ************************************

    private void addIntegrationSource(Integration integration, ImmutableIntegrationSpec.Builder builder) throws IOException {
        final String json = extractIntegrationJson(integration);
        final String content = compress ? CamelKSupport.compress(json) : json;
        final String name = integration.getId().get();

        logInfo(integration,"integration.json: {}", content);

        builder.addSources(new SourceSpec.Builder()
            .compression(compress)
            .content(content)
            .language("syndesis")
            .name(Names.sanitize(name))
            .build());
    }

    private void addMappingRules(Integration integration, ImmutableIntegrationSpec.Builder builder) throws IOException {
        final List<Flow> flows = integration.getFlows();
        for (int f = 0; f < flows.size(); f++) {
            final Flow flow = flows.get(f);
            final List<Step> steps = flow.getSteps();

            for (int s = 0; s < steps.size(); s++) {
                final Step step = steps.get(s);

                if (StepKind.mapper == step.getStepKind()) {
                    final Map<String, String> properties = step.getConfiguredProperties();
                    final String name = "mapping-flow-" + f + "-step-"  + s + ".json";
                    final String mapping = properties.get("atlasmapping");
                    final String content = compress ? CamelKSupport.compress(mapping) : mapping;

                    if (content != null) {
                        builder.addResources(
                            new ResourceSpec.Builder()
                                .compression(compress)
                                .name(Names.sanitize(name))
                                .content(content)
                                .type("data")
                            .build()
                        );
                    }
                }
            }
        }
    }

    private void addOpenAPIDefinition(Integration integration, ImmutableIntegrationSpec.Builder builder) throws IOException {
        // assuming that we have a single swagger definition for the moment
        Optional<ResourceIdentifier> rid = integration.getResources().stream().filter(Kind.OpenApi::sameAs).findFirst();
        if (!rid.isPresent()) {
            return;
        }

        final ResourceIdentifier openApiResource = rid.get();
        final Optional<String> maybeOpenApiResourceId = openApiResource.getId();
        if (!maybeOpenApiResourceId.isPresent()) {
            return;
        }

        final String openApiResourceId = maybeOpenApiResourceId.get();
        Optional<OpenApi> res = resourceManager.loadOpenApiDefinition(openApiResourceId);
        if (!res.isPresent()) {
            return;
        }

        final byte[] openApiBytes = res.get().getDocument();
        final String content = compress ? CamelKSupport.compress(openApiBytes) : new String(openApiBytes, UTF_8);
        final String name = openApiResource.name().orElse(maybeOpenApiResourceId.get());

        builder.addResources(
            new ResourceSpec.Builder()
                .compression(compress)
                .name(Names.sanitize(name))
                .content(content)
                .type("openapi")
                .build()
        );
    }

}
