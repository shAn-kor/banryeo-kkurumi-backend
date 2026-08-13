package com.banryeokkurumi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenApiContractIntegrationTest {
    private final ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @Autowired
    OpenApiContractIntegrationTest(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Test
    void openApi_Kotlin전환뒤결제계약snapshot을유지한다() throws Exception {
        JsonNode expected = objectMapper.readTree(Files.readString(Path.of("config/openapi-payment-snapshot.json")));
        JsonNode openApi = getJson("/v3/api-docs");
        JsonNode operation = openApi.at("/paths/~1api~1v1~1orders~1{orderId}~1payment/get");
        JsonNode schema = openApi.at("/components/schemas/PaymentView");

        ObjectNode actual = objectMapper.createObjectNode();
        actual.put("path", "/api/v1/orders/{orderId}/payment");
        actual.put("method", "get");
        actual.put("schema", operation.at("/responses/200/content/*~1*/schema/$ref").asString().replace("#/components/schemas/", ""));
        actual.set("fields", objectMapper.valueToTree(fieldSignatures(schema.path("properties").properties().iterator())));

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void publicSearch_인증없이정확한root경로를호출할수있다() throws Exception {
        HttpResponse<String> response = get("/api/v1/search?sort=POPULAR");

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private Map<String, String> fieldSignatures(Iterator<Map.Entry<String, JsonNode>> fields) {
        Map<String, String> signatures = new TreeMap<>();
        fields.forEachRemaining(field -> signatures.put(
                field.getKey(), field.getValue().path("type").asString() + ":" + field.getValue().path("format").asString()));
        return signatures;
    }

    private JsonNode getJson(String path) throws Exception {
        return objectMapper.readTree(get(path).body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
