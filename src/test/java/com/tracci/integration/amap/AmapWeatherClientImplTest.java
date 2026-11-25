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

class AmapWeatherClientImplTest {

    @Test
    void forecast_rejectsMissingKey() {
        AmapWeatherClientImpl client = new AmapWeatherClientImpl(properties(""), RestClient.builder(), new ObjectMapper());

        assertThrows(BizException.class, () -> client.forecast("330106"));
    }

    @Test
    void forecast_sendsAllForecastParams() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://restapi.amap.com/v3/weather/weatherInfo")))
                .andExpect(queryParam("city", "330106"))
                .andExpect(queryParam("extensions", "all"))
                .andExpect(queryParam("output", "JSON"))
                .andRespond(withSuccess("""
                        {"status":"1","info":"OK","infocode":"10000","count":"1","forecasts":[{"city":"杭州","adcode":"330106","province":"浙江","reporttime":"2026-05-22 10:00:00","casts":[]}]}
                        """, org.springframework.http.MediaType.APPLICATION_JSON));
        AmapWeatherClientImpl client = new AmapWeatherClientImpl(properties("test-key"), builder, new ObjectMapper());

        var resp = client.forecast("330106");

        assertEquals("1", resp.status());
        server.verify();
    }

    @Test
    void forecast_rejectsAmapErrorCode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://restapi.amap.com/v3/weather/weatherInfo?key=test-key&city=330106&extensions=all&output=JSON"))
                .andRespond(withSuccess("{\"status\":\"0\",\"info\":\"INVALID_USER_KEY\",\"infocode\":\"10001\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        AmapWeatherClientImpl client = new AmapWeatherClientImpl(properties("test-key"), builder, new ObjectMapper());

        assertThrows(ThirdPartyException.class, () -> client.forecast("330106"));
        server.verify();
    }

    private AmapProperties properties(String key) {
        AmapProperties properties = new AmapProperties();
        properties.setEnabled(true);
        properties.setWebServiceKey(key);
        properties.getWeather().setEnabled(true);
        properties.getWeather().setEndpoint("https://restapi.amap.com/v3/weather/weatherInfo");
        return properties;
    }
}
