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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;
import java.util.zip.GZIPOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableSet;
import io.fabric8.kubernetes.api.model.Secret;
import io.syndesis.common.util.SyndesisServerException;

public final class CamelKSupport {
    //    // IntegrationPhaseInitial --
    //    IntegrationPhaseInitial IntegrationPhase = ""
    //    // IntegrationPhaseWaitingForPlatform --
    //    IntegrationPhaseWaitingForPlatform IntegrationPhase = "Waiting For Platform"
    //    // IntegrationPhaseBuildingContext --
    //    IntegrationPhaseBuildingContext IntegrationPhase = "Building Context"
    //    // IntegrationPhaseBuildImageSubmitted --
    //    IntegrationPhaseBuildImageSubmitted IntegrationPhase = "Build Image Submitted"
    //    // IntegrationPhaseBuildImageRunning --
    //    IntegrationPhaseBuildImageRunning IntegrationPhase = "Build Image Running"
    //    // IntegrationPhaseDeploying --
    //    IntegrationPhaseDeploying IntegrationPhase = "Deploying"
    //    // IntegrationPhaseRunning --
    //    IntegrationPhaseRunning IntegrationPhase = "Running"
    //    // IntegrationPhaseError --
    //    IntegrationPhaseError IntegrationPhase = "Error"
    //    // IntegrationPhaseBuildFailureRecovery --
    //    IntegrationPhaseBuildFailureRecovery IntegrationPhase = "Building Failure Recovery"

    public static final ImmutableSet<String> CAMEL_K_STARTED_STATES = ImmutableSet.of(
                "Waiting For Platform",
                "Building Context",
                "Build Image Submitted",
                "Build Image Running",
                "Deploying");
    public static final ImmutableSet<String> CAMEL_K_FAILED_STATES = ImmutableSet.of(
                "Error",
                "Building Failure Recovery");
    public static final ImmutableSet<String> CAMEL_K_READY_STATES = ImmutableSet.of(
                "Running");
    public static final ImmutableSet<String> CAMEL_K_RUNNING_STATES = ImmutableSet.of(
                "Running" );

    public static final String CAMEL_K_INTEGRATION_CRD_NAME = "integrations.camel.apache.org";

    private CamelKSupport() {
    }

    public static String compress(String data) throws IOException {
        if (data == null) {
            return null;
        }

        return compress(data.getBytes(UTF_8));
    }

    public static String compress(byte[] data) throws IOException {
        if (data == null) {
            return null;
        }

        try(
            ByteArrayOutputStream os = new ByteArrayOutputStream(data.length);
            GZIPOutputStream gzip = new GZIPOutputStream(os)
        ) {
            gzip.write(data);
            return os.toString(UTF_8.name());
        }
    }

    public static String propsToString(Properties data) {
        if (data == null) {
            return "";
        }

        try {
            StringWriter w = new StringWriter();
            data.store(w, "");
            return w.toString();
        } catch (IOException e) {
            throw SyndesisServerException.launderThrowable(e);
        }
    }

    public static Properties secretToProperties(Secret secret) {
        Properties properties = new Properties();
        if (secret == null) {
            return properties;
        }

        String data = secret.getStringData().get("application.properties");
        if (data == null) {
            return properties;
        }

        try(Reader reader = new StringReader(data)) {
            properties.load(reader);
        } catch (IOException e) {
            throw SyndesisServerException.launderThrowable(e);
        }

        return properties;
    }
}
