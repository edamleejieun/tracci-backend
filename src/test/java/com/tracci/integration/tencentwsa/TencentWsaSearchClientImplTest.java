package com.tracci.integration.tencentwsa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TencentWsaSearchClientImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void search_defaultMode_omitsModeParameter() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TencentWsaSearchClientImpl client = new TencentWsaSearchClientImpl(
                properties(null), builder.build(), objectMapper);

        server.expect(requestTo("https://wsa.tencentcloudapi.com"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("{\"Query\":\"重庆市 旅游攻略\",\"Cnt\":10}"))
                .andRespond(withSuccess("{\"Response\":{\"Pages\":[]}}", MediaType.APPLICATION_JSON));

        client.search("重庆市 旅游攻略", 8);

        server.verify();
    }

    @Test
    void search_configuredMode_sendsModeParameter() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TencentWsaSearchClientImpl client = new TencentWsaSearchClientImpl(
                properties(1), builder.build(), objectMapper);

        server.expect(requestTo("https://wsa.tencentcloudapi.com"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("{\"Query\":\"重庆市 旅游攻略\",\"Cnt\":10,\"Mode\":1}"))
                .andRespond(withSuccess("{\"Response\":{\"Pages\":[]}}", MediaType.APPLICATION_JSON));

        client.search("重庆市 旅游攻略", 8);

        server.verify();
    }

    private static TencentWsaProperties properties(Integer mode) {
        TencentWsaProperties properties = new TencentWsaProperties();
        properties.setEnabled(true);
        properties.setSecretId("test-secret-id");
        properties.setSecretKey("test-secret-key");
        properties.setMode(mode);
        return properties;
    }
}
