package com.tracci.integration.amap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ThirdPartyException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapPoiClientImplTest {

    @Test
    void searchText_rejectsMissingKey() {
        AmapProperties properties = properties("");
        AmapPoiClientImpl client = new AmapPoiClientImpl(properties, RestClient.builder(), new ObjectMapper());

        assertThrows(BizException.class, () -> client.searchText("西湖", null, 1, 10, null));
    }

    @Test
    void searchText_rejectsAmapErrorCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://restapi.amap.com/v5/place/text?key=test-key&keywords=%E8%A5%BF%E6%B9%96&page_num=1&page_size=10&show_fields=children,business,indoor,navi,photos"))
                .andRespond(withSuccess("{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\",\"infocode\":\"10001\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        AmapPoiClientImpl client = new AmapPoiClientImpl(properties("test-key"), builder, new ObjectMapper());

        assertThrows(ThirdPartyException.class, () -> client.searchText("西湖", null, 1, 10, null));
        server.verify();
    }

    @Test
    void searchText_sendsCityLimitParams() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://restapi.amap.com/v5/place/text")))
                .andExpect(queryParam("region", "%E6%9D%AD%E5%B7%9E"))
                .andExpect(queryParam("city_limit", "true"))
                .andExpect(queryParam("types", "110000"))
                .andRespond(withSuccess("{\"status\":\"1\",\"info\":\"OK\",\"infocode\":\"10000\",\"count\":\"0\",\"pois\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));
        AmapPoiClientImpl client = new AmapPoiClientImpl(properties("test-key"), builder, new ObjectMapper());

        client.searchText("西湖", "杭州", 1, 10, "110000");
        server.verify();
    }

    @Test
    void getDetail_queriesProviderIdAndReturnsVerifiedPoi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "https://restapi.amap.com/v5/place/detail")))
                .andExpect(queryParam("id", "B001"))
                .andRespond(withSuccess("""
                        {"status":"1","info":"OK","infocode":"10000","count":"1",
                         "pois":[{"id":"B001","name":"西湖","location":"120.1,30.2","typecode":"110000"}]}
                        """, org.springframework.http.MediaType.APPLICATION_JSON));
        AmapPoiClientImpl client = new AmapPoiClientImpl(properties("test-key"), builder, new ObjectMapper());

        var poi = client.getDetail("B001");

        assertEquals("B001", poi.id());
        assertEquals("西湖", poi.name());
        server.verify();
    }

    private AmapProperties properties(String key) {
        AmapProperties properties = new AmapProperties();
        properties.setEnabled(true);
        properties.setWebServiceKey(key);
        properties.getPoi().setEndpoint("https://restapi.amap.com/v5/place/text");
        properties.getPoi().setDetailEndpoint("https://restapi.amap.com/v5/place/detail");
        return properties;
    }
}
