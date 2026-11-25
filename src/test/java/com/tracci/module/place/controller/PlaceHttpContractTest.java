package com.tracci.module.place.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.GlobalExceptionHandler;
import com.tracci.common.response.PageResponse;
import com.tracci.module.place.dto.request.PublishedPlaceListQuery;
import com.tracci.module.place.dto.response.PlaceCoverResponse;
import com.tracci.module.place.dto.response.PlaceDetailResponse;
import com.tracci.module.place.dto.response.PlaceListItemResponse;
import com.tracci.module.place.dto.response.PlaceUserContext;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.placecontent.dto.response.ContentMetaDto;
import com.tracci.module.placecontent.dto.response.EditorialContentDto;
import com.tracci.module.savedplace.service.SavedPlaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaceHttpContractTest {

    @Mock
    private PlaceService placeService;

    @Mock
    private SavedPlaceService savedPlaceService;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void listPublishedPlaces_omitsHeavyFieldsAndAssemblesIsSaved() throws Exception {
        PlaceListItemResponse unsaved = new PlaceListItemResponse(
                "place-1", "清水寺", "POI", "ATTRACTION", "京都", "日本",
                null, null, List.of("ITINERARY_NODE"), "山门到舞台",
                LocalDateTime.of(2026, 9, 1, 8, 0, 0), false, null);
        PlaceListItemResponse saved = new PlaceListItemResponse(
                "place-2", "金阁寺", "POI", "ATTRACTION", "京都", "日本",
                null, null, List.of("ITINERARY_NODE"), null,
                LocalDateTime.of(2026, 9, 1, 7, 0, 0), false,
                new PlaceCoverResponse("media-2", "https://cdn.example/kinkaku.jpg", "金阁", null));
        when(placeService.listPublishedPlaces(new PublishedPlaceListQuery("京都", "POI", null, 1, 50)))
                .thenReturn(PageResponse.of(List.of(unsaved, saved), 2, 1, 50));
        when(savedPlaceService.findSavedPlaceIds("u-1", List.of("place-1", "place-2")))
                .thenReturn(Set.of("place-2"));
        LoginUserContext.setUserId("u-1");

        mockMvc().perform(get("/api/v1/places")
                        .param("placeScope", "POI")
                        .param("cityName", "京都")
                        .param("pageSize", "50"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        "private, no-cache, max-age=0, must-revalidate"))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(50))
                .andExpect(jsonPath("$.data.list[0].id").value("place-1"))
                .andExpect(jsonPath("$.data.list[0].isSaved").value(false))
                .andExpect(jsonPath("$.data.list[0].highlight").value("山门到舞台"))
                .andExpect(jsonPath("$.data.list[0].cover").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].editorialContent").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].media").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].contentMeta").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].userContext").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].address").doesNotExist())
                .andExpect(jsonPath("$.data.list[1].isSaved").value(true))
                .andExpect(jsonPath("$.data.list[1].highlight").doesNotExist())
                .andExpect(jsonPath("$.data.list[1].cover.thumbnailUrl")
                        .value("https://cdn.example/kinkaku.jpg"));
    }

    @Test
    void listPublishedPlaces_emptyListIsSuccess() throws Exception {
        when(placeService.listPublishedPlaces(any(PublishedPlaceListQuery.class)))
                .thenReturn(PageResponse.of(List.of(), 0, 1, 20));
        when(savedPlaceService.findSavedPlaceIds(eq("u-1"), eq(List.of()))).thenReturn(Set.of());
        LoginUserContext.setUserId("u-1");

        mockMvc().perform(get("/api/v1/places").param("placeScope", "DESTINATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isEmpty());

        verify(placeService).listPublishedPlaces(new PublishedPlaceListQuery(null, "DESTINATION", null, 1, 20));
    }

    @Test
    void getDetail_omitsNullableSavedFieldsWhenPlaceIsNotSaved() throws Exception {
        when(placeService.getPlaceDetail("place-1")).thenReturn(new PlaceDetailResponse(
                "place-1", "浅草寺", "POI", "ATTRACTION", null, "东京", "日本",
                null, null, List.of("ITINERARY_NODE"), null, null, null, null));
        when(savedPlaceService.buildUserContext("u-1", "place-1"))
                .thenReturn(new PlaceUserContext(false, null, null, null));
        LoginUserContext.setUserId("u-1");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller())
                .build();

        mockMvc.perform(get("/api/v1/places/place-1"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        "private, no-cache, max-age=0, must-revalidate"))
                .andExpect(jsonPath("$.data.userContext.isSaved").value(false))
                .andExpect(jsonPath("$.data.userContext.savedPlaceId").doesNotExist())
                .andExpect(jsonPath("$.data.userContext.note").doesNotExist())
                .andExpect(jsonPath("$.data.userContext.usageSummary").doesNotExist())
                .andExpect(jsonPath("$.data.editorialContent").doesNotExist())
                .andExpect(jsonPath("$.data.media").doesNotExist());
    }

    @Test
    void getDetail_returnsEditorialWhenPublished() throws Exception {
        EditorialContentDto editorial = new EditorialContentDto(
                3, "介绍", List.of("亮点"), 120, List.of("建议"), "春秋", "预约", "注意");
        ContentMetaDto meta = new ContentMetaDto(true, "AI 辅助整理 · 已审核", null, null, false);
        when(placeService.getPlaceDetail("place-1")).thenReturn(new PlaceDetailResponse(
                "place-1", "浅草寺", "POI", "ATTRACTION", null, "东京", "日本",
                null, null, List.of("ITINERARY_NODE"), null, editorial, meta, null));
        when(savedPlaceService.buildUserContext("u-1", "place-1"))
                .thenReturn(new PlaceUserContext(false, null, null, null));
        LoginUserContext.setUserId("u-1");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller())
                .build();

        mockMvc.perform(get("/api/v1/places/place-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editorialContent.version").value(3))
                .andExpect(jsonPath("$.data.editorialContent.introduction").value("介绍"))
                .andExpect(jsonPath("$.data.contentMeta.aiAssisted").value(true))
                .andExpect(jsonPath("$.data.contentMeta.label").value("AI 辅助整理 · 已审核"));
    }

    @Test
    void getDetail_returns304WhenETagMatches() throws Exception {
        when(placeService.getPlaceDetail("place-1")).thenReturn(new PlaceDetailResponse(
                "place-1", "浅草寺", "POI", "ATTRACTION", null, "东京", "日本",
                null, null, List.of("ITINERARY_NODE"), null, null, null, null));
        when(savedPlaceService.buildUserContext("u-1", "place-1"))
                .thenReturn(new PlaceUserContext(false, null, null, null));
        LoginUserContext.setUserId("u-1");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller())
                .build();

        MvcResult first = mockMvc.perform(get("/api/v1/places/place-1"))
                .andExpect(status().isOk())
                .andReturn();
        String eTag = first.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(eTag).isNotBlank();

        mockMvc.perform(get("/api/v1/places/place-1").header(HttpHeaders.IF_NONE_MATCH, eTag))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.ETAG, eTag));
    }

    @Test
    void getDetail_usesCanonicalPlaceIdForUserContext() throws Exception {
        when(placeService.getPlaceDetail("merged-1")).thenReturn(new PlaceDetailResponse(
                "canonical-1", "浅草寺", "POI", "ATTRACTION", null, "东京", "日本",
                null, null, List.of("ITINERARY_NODE"), null, null, null, null));
        when(savedPlaceService.buildUserContext("u-1", "canonical-1"))
                .thenReturn(new PlaceUserContext(true, "saved-1", "note", null));
        LoginUserContext.setUserId("u-1");
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller())
                .build();

        mockMvc.perform(get("/api/v1/places/merged-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("canonical-1"))
                .andExpect(jsonPath("$.data.userContext.isSaved").value(true))
                .andExpect(jsonPath("$.data.userContext.savedPlaceId").value("saved-1"));
    }

    @Test
    void getDetail_doesNotReturn304WhenAnyResponseFieldChanges() throws Exception {
        PlaceDetailResponse attraction = new PlaceDetailResponse(
                "place-1", "浅草寺", "POI", "ATTRACTION", null, "东京", "日本",
                null, null, List.of("ITINERARY_NODE"), null, null, null, null);
        PlaceDetailResponse cultural = new PlaceDetailResponse(
                "place-1", "浅草寺", "POI", "CULTURAL", null, "东京", "日本",
                null, null, List.of("ITINERARY_NODE"), null, null, null, null);
        when(placeService.getPlaceDetail("place-1")).thenReturn(attraction, cultural);
        when(savedPlaceService.buildUserContext("u-1", "place-1"))
                .thenReturn(new PlaceUserContext(false, null, null, null));
        LoginUserContext.setUserId("u-1");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller()).build();

        String firstETag = mockMvc.perform(get("/api/v1/places/place-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader(HttpHeaders.ETAG);

        MvcResult changed = mockMvc.perform(get("/api/v1/places/place-1")
                        .header(HttpHeaders.IF_NONE_MATCH, firstETag))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(changed.getResponse().getHeader(HttpHeaders.ETAG)).isNotEqualTo(firstETag);
    }

    private PlaceController controller() {
        return new PlaceController(placeService, savedPlaceService,
                new ObjectMapper().findAndRegisterModules());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(controller())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
