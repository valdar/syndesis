package io.syndesis.connector.knative;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.syndesis.connector.knative.KnativeComponentProxyFactory.computeKnativeUri;
import static org.assertj.core.api.Assertions.assertThat;

public class KnativeComponentFactoryTest {

    @Test
    public void testComputeKnativeUri() throws Exception {
        assertThat(computeKnativeUri("knative",
            mapOf("type", "channel", "name", "test")))
            .isEqualTo("knative://channel/test");

        assertThat(computeKnativeUri("knative",
            mapOf("type", "channel", "name", "test", "unknown", "false")))
            .isEqualTo("knative://channel/test?unknown=false");

        assertThat(computeKnativeUri("knative",
            mapOf("type", "endpoint", "name", "test2")))
            .isEqualTo("knative://endpoint/test2");

    }

    // *************************
    // Helpers
    // *************************

    private static Map<String, String> mapOf(String key, String value, String... values) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(key, value);

        for (int i = 0; i < values.length; i += 2) {
            map.put(
                values[i],
                values[i + 1]
            );
        }

        return map;
    }

}
