package com.tracci.module.place.service;

import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.integration.amap.AmapPoiClient;
import com.tracci.module.place.dto.request.PublishedPlaceListQuery;
import com.tracci.module.place.dto.response.PlaceListItemResponse;
import com.tracci.module.place.repository.PlaceMapper;
import com.tracci.module.place.repository.PlaceProviderRefMapper;
import com.tracci.module.place.repository.PublishedPlaceRow;
import com.tracci.module.placecontent.dto.response.MediaAssetDto;
import com.tracci.module.placecontent.service.PlaceContentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlacePublishedListServiceTest {

    @Mock
    private PlaceMapper placeMapper;

    @Mock
    private PlaceProviderRefMapper placeProviderRefMapper;

    @Mock
    private AmapPoiClient amapPoiClient;

    @Mock
    private PlaceContentService placeContentService;

    @InjectMocks
    private PlaceServiceImpl placeService;

    @Test
    void listPublishedPlaces_returnsEmptyPageWhenNonePublished() {
        when(placeMapper.countPublishedPlaces("DESTINATION", null, null)).thenReturn(0L);

        PageResponse<PlaceListItemResponse> result = placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(null, "DESTINATION", null, 1, 50));

        assertThat(result.total()).isZero();
        assertThat(result.list()).isEmpty();
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(50);
        verify(placeMapper, never()).selectPublishedPlaces(
                eq("DESTINATION"), isNull(), isNull(), eq(50), eq(0L));
    }

    @Test
    void listPublishedPlaces_trimsCityNameAndFiltersPoi() {
        when(placeMapper.countPublishedPlaces("POI", "京都", null)).thenReturn(1L);
        when(placeMapper.selectPublishedPlaces("POI", "京都", null, 50, 0L))
                .thenReturn(List.of(poiRow("place-1", "清水寺", "京都", "[\"山门到舞台\"]", null)));
        when(placeContentService.findCoverMediaByPlaceIds(List.of("place-1"))).thenReturn(Map.of());

        PageResponse<PlaceListItemResponse> result = placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(" 京都 ", "POI", null, 1, 50));

        assertThat(result.total()).isEqualTo(1);
        PlaceListItemResponse item = result.list().get(0);
        assertThat(item.id()).isEqualTo("place-1");
        assertThat(item.name()).isEqualTo("清水寺");
        assertThat(item.placeScope()).isEqualTo("POI");
        assertThat(item.supportedUsages()).containsExactly("ITINERARY_NODE");
        assertThat(item.highlight()).isEqualTo("山门到舞台");
        assertThat(item.cover()).isNull();
        assertThat(item.isSaved()).isFalse();
        assertThat(item.cityName()).isEqualTo("京都");
    }

    @Test
    void listPublishedPlaces_usesFirstNonBlankHighlightThenTruncatesIntroduction() {
        String forty = "一二三四五六七八九十".repeat(4);
        when(placeMapper.countPublishedPlaces(isNull(), isNull(), isNull())).thenReturn(2L);
        when(placeMapper.selectPublishedPlaces(isNull(), isNull(), isNull(), eq(20), eq(0L)))
                .thenReturn(List.of(
                        poiRow("p-highlight", "A", "京都", "[\"\", \"  \", \"真正亮点\"]", "介绍"),
                        poiRow("p-intro", "B", "京都", "[]", forty + "尾")));
        when(placeContentService.findCoverMediaByPlaceIds(anyList())).thenReturn(Map.of());

        List<PlaceListItemResponse> items = placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(null, null, null, 1, 20)).list();

        assertThat(items.get(0).highlight()).isEqualTo("真正亮点");
        assertThat(items.get(1).highlight()).isEqualTo(forty + "…");
    }

    @Test
    void listPublishedPlaces_omitsHighlightWhenBothEmpty() {
        when(placeMapper.countPublishedPlaces(isNull(), isNull(), isNull())).thenReturn(1L);
        when(placeMapper.selectPublishedPlaces(isNull(), isNull(), isNull(), eq(20), eq(0L)))
                .thenReturn(List.of(poiRow("p-empty", "C", "京都", "[]", "  ")));
        when(placeContentService.findCoverMediaByPlaceIds(List.of("p-empty"))).thenReturn(Map.of());

        assertThat(placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(null, null, null, 1, 20)).list().get(0).highlight()).isNull();
    }

    @Test
    void listPublishedPlaces_coverFallsBackPreviewThenUrl() {
        when(placeMapper.countPublishedPlaces(isNull(), isNull(), isNull())).thenReturn(1L);
        when(placeMapper.selectPublishedPlaces(isNull(), isNull(), isNull(), eq(20), eq(0L)))
                .thenReturn(List.of(poiRow("p-cover", "D", "京都", null, null)));
        when(placeContentService.findCoverMediaByPlaceIds(List.of("p-cover"))).thenReturn(Map.of(
                "p-cover", new MediaAssetDto(
                        "media-1", "https://cdn.example/orig.jpg", null,
                        "https://cdn.example/preview.jpg", 1600, 900, "舞台", "授权")));

        PlaceListItemResponse item = placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(null, null, null, 1, 20)).list().get(0);

        assertThat(item.cover()).isNotNull();
        assertThat(item.cover().id()).isEqualTo("media-1");
        assertThat(item.cover().thumbnailUrl()).isEqualTo("https://cdn.example/preview.jpg");
        assertThat(item.cover().altText()).isEqualTo("舞台");
        assertThat(item.cover().attribution()).isEqualTo("授权");
    }

    @Test
    void listPublishedPlaces_destinationUsagesAndDefaultPageSize() {
        when(placeMapper.countPublishedPlaces("DESTINATION", null, "CITY")).thenReturn(1L);
        PublishedPlaceRow city = poiRow("city-1", "京都", null, "[\"古都\"]", null);
        city.setPlaceScope("DESTINATION");
        city.setCategory("CITY");
        city.setCountry("日本");
        when(placeMapper.selectPublishedPlaces("DESTINATION", null, "CITY", 20, 0L))
                .thenReturn(List.of(city));
        when(placeContentService.findCoverMediaByPlaceIds(List.of("city-1"))).thenReturn(Map.of());

        PlaceListItemResponse item = placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(null, "DESTINATION", "CITY", 1, 20)).list().get(0);

        assertThat(item.supportedUsages()).containsExactly("TRIP_DESTINATION");
        assertThat(item.highlight()).isEqualTo("古都");
        assertThat(item.country()).isEqualTo("日本");
    }

    @Test
    void listPublishedPlaces_rejectsIllegalPaginationAndEnums() {
        assertThatThrownBy(() -> placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(null, null, null, 0, 20)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        assertThatThrownBy(() -> placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(null, null, null, 1, 51)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);

        assertThatThrownBy(() -> placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(null, "CITY", null, 1, 20)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY);

        assertThatThrownBy(() -> placeService.listPublishedPlaces(
                new PublishedPlaceListQuery(null, "POI", "NOT_A_CATEGORY", 1, 20)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PLACE_INVALID_SCOPE_CATEGORY);
    }

    private static PublishedPlaceRow poiRow(
            String id, String name, String cityName, String highlightsJson, String introduction) {
        PublishedPlaceRow row = new PublishedPlaceRow();
        row.setId(id);
        row.setCanonicalName(name);
        row.setPlaceScope("POI");
        row.setCategory("ATTRACTION");
        row.setCityName(cityName);
        row.setLatitude(new BigDecimal("34.9949"));
        row.setLongitude(new BigDecimal("135.7850"));
        row.setPublishedAt(LocalDateTime.of(2026, 9, 1, 8, 0, 0));
        row.setHighlightsJson(highlightsJson);
        row.setIntroduction(introduction);
        return row;
    }
}
