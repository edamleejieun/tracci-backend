package com.tracci.integration.tencentwsa;

import com.fasterxml.jackson.databind.JsonNode;

public interface TencentWsaSearchClient {

    JsonNode search(String query, Integer limit);
}
