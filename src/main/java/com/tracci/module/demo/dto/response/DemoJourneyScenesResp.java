package com.tracci.module.demo.dto.response;

public record DemoJourneyScenesResp(
        DemoJourneySceneResp planning,
        DemoJourneySceneResp ongoing,
        DemoJourneySceneResp completed
) {
}
