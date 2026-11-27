package com.scaler.productcatalogservice.clients;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// Checks the request that goes to the numbers service and the answer that comes back, without any real call.
class EmbeddingClientTest {

    @Test
    void embed_sendsOneRequestPerTextWithTheKeyAndReadsTheNumbers() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EmbeddingClient client = new EmbeddingClient(builder, mock(DiscoveryClient.class), "http://numbers.test/v1beta", "my-key", false);
        server.expect(requestTo("http://numbers.test/v1beta/models/gemini-embedding-001:batchEmbedContents"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "my-key"))
                .andExpect(jsonPath("$.requests.length()").value(2))
                .andExpect(jsonPath("$.requests[0].model").value("models/gemini-embedding-001"))
                .andExpect(jsonPath("$.requests[0].content.parts[0].text").value("red lipstick"))
                .andExpect(jsonPath("$.requests[0].taskType").value("RETRIEVAL_DOCUMENT"))
                .andExpect(jsonPath("$.requests[1].outputDimensionality").value(768))
                .andRespond(withSuccess("{\"embeddings\":[{\"values\":[1.0,0.0,0.5]},{\"values\":[0.0,1.0,0.25]}]}",
                        MediaType.APPLICATION_JSON));

        List<float[]> numbers = client.embed(List.of("red lipstick", "a lamp"), "RETRIEVAL_DOCUMENT");

        assertEquals(2, numbers.size());
        assertArrayEquals(new float[]{1.0f, 0.0f, 0.5f}, numbers.get(0));
        assertArrayEquals(new float[]{0.0f, 1.0f, 0.25f}, numbers.get(1));
        assertEquals("0.5,-0.25,3.0", EmbeddingClient.toText(new float[]{0.5f, -0.25f, 3f}));
        assertArrayEquals(new float[]{0.5f, -0.25f, 3f}, EmbeddingClient.toNumbers("0.5,-0.25,3.0"));
        server.verify();
    }

    @Test
    void discoveryReReadsTheAddressForEveryCallAndDoesNotSendTheProviderKey() {
        DiscoveryClient discovery = mock(DiscoveryClient.class);
        when(discovery.getInstances("embedding-service"))
                .thenReturn(List.of(new DefaultServiceInstance("first", "embedding-service", "localhost", 9090, false)))
                .thenReturn(List.of(new DefaultServiceInstance("second", "embedding-service", "localhost", 9091, false)))
                .thenReturn(List.of());
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EmbeddingClient client = new EmbeddingClient(builder, discovery, "http://unused.test", "provider-key", true);
        for (int port : List.of(9090, 9091)) {
            server.expect(requestTo("http://localhost:" + port + "/v1beta/models/gemini-embedding-001:batchEmbedContents"))
                    .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist("x-goog-api-key"))
                    .andRespond(withSuccess("{\"embeddings\":[{\"values\":[1.0]}]}", MediaType.APPLICATION_JSON));
        }
        assertArrayEquals(new float[]{1}, client.embed(List.of("lipstick"), "RETRIEVAL_QUERY").get(0));
        assertArrayEquals(new float[]{1}, client.embed(List.of("lipstick"), "RETRIEVAL_QUERY").get(0));
        assertThrows(RestClientException.class, () -> client.embed(List.of("lipstick"), "RETRIEVAL_QUERY"));
        server.verify();
    }
}
