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
package io.syndesis.server.runtime;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.CustomResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertNotNull;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(
    classes = {
        KubernetesClient.class
    },
    properties = {
        "spring.main.banner-mode = off"
    }
)
public class CdrITCase {

    @Autowired
    private KubernetesClient client;

    @Test
    public void importCdrAndCreateCrTest() throws IOException {
        InputStream cdrYamlStream = null;
        try {
            cdrYamlStream = this.getClass().getClassLoader()
                .getResourceAsStream("crd-integration.yaml");
            client.load(cdrYamlStream).createOrReplace();
        } finally {
            if(cdrYamlStream != null){
                cdrYamlStream.close();
            }
        }

        CustomResourceDefinitionList crds = client.customResourceDefinitions().list();
        List<CustomResourceDefinition> crdsItems = crds.getItems();
        CustomResourceDefinition integrationCRD = null;
        for (CustomResourceDefinition crd : crdsItems) {
            ObjectMeta metadata = crd.getMetadata();
            if (metadata != null) {
                String name = metadata.getName();
                if ("integrations.camel.apache.org".equals(name)) {
                    integrationCRD = crd;
                }
            }
        }

        assertNotNull(integrationCRD);


        // lets create a client for the CRD
        NonNamespaceOperation<Integration, IntegrationList, DoneableIntegration, Resource<Integration, DoneableIntegration>> integrationClient = client.customResource(integrationCRD, Integration.class, IntegrationList.class, DoneableIntegration.class)
            .inNamespace("myproject");
        CustomResourceList<Integration> integrationList = integrationClient.list();
        List<Integration> items = integrationList.getItems();
        for (Integration item : items) {
            System.out.println("    " + item);
        }

        Integration integration = new Integration();
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName("example");
        integration.setMetadata(metadata);
        IntegrationSpec integrationSpec = new IntegrationSpec();

        SourceSpec sp = new SourceSpec();
        sp.setName("routes.groovy");
        sp.setContent("      // This is Camel K Groovy example route\n" +
            "\n" +
            "      rnd = new Random()\n" +
            "\n" +
            "      from('timer:groovy?period=1s')\n" +
            "          .routeId('groovy')\n" +
            "          .setBody()\n" +
            "              .constant('Hello Camel K!')\n" +
            "          .process {\n" +
            "              it.in.headers['RandomValue'] = rnd.nextInt()\n" +
            "          }\n" +
            "          .to('log:info?showHeaders=true')");
        integrationSpec.addSourceSpec(sp);
        integration.setSpec(integrationSpec);

        integrationClient.createOrReplace(integration);
    }

}
