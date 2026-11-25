package com.tracci.module.placecontent.controller;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.GlobalExceptionHandler;
import com.tracci.common.response.PageResponse;
import com.tracci.module.placecontent.dto.request.PlaceAdminSearchQuery;
import com.tracci.module.placecontent.dto.request.PlaceResolveFromPoiRequest;
import com.tracci.module.placecontent.dto.response.PlaceAdminSearchItem;
import com.tracci.module.placecontent.service.PlaceAdminResolveService;
import com.tracci.module.placecontent.service.PlaceContentService;
import com.tracci.service.AdminService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaceAdminSearchHttpContractTest {

    @Mock
    private PlaceContentService placeContentService;

    @Mock
    private PlaceAdminResolveService placeAdminResolveService;

    @Mock
    private AdminService adminService;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void search_adminPassesQueryParamsToService() throws Exception {
        LoginUserContext.setUserId("admin-001");
        when(adminService.isCurrentUserAdmin()).thenReturn(true);
        when(placeContentService.searchPlacesForAdmin(any(PlaceAdminSearchQuery.class))).thenReturn(
                PageResponse.of(List.of(), 0, 1, 20));

        mockMvc().perform(get("/internal/v1/places")
                        .param("keyword", "故宫")
                        .param("cityName", "北京")
                        .param("placeScope", "POI")
                        .param("category", "ATTRACTION")
                        .param("contentState", "NONE")
                        .param("page", "2")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));

        verify(placeContentService).searchPlacesForAdmin(
                new PlaceAdminSearchQuery("故宫", null, "北京", "POI", "ATTRACTION", "NONE", 2, 10));
    }

    @Test
    void search_defaultsPageAndPageSize() throws Exception {
        LoginUserContext.setUserId("admin-001");
        when(adminService.isCurrentUserAdmin()).thenReturn(true);
        when(placeContentService.searchPlacesForAdmin(any(PlaceAdminSearchQuery.class))).thenReturn(
                PageResponse.of(List.<PlaceAdminSearchItem>of(), 0, 1, 20));

        mockMvc().perform(get("/internal/v1/places"))
                .andExpect(status().isOk());

        verify(placeContentService).searchPlacesForAdmin(
                new PlaceAdminSearchQuery(null, null, null, null, null, null, 1, 20));
    }

    @Test
    void search_nonAdmin_isForbidden() throws Exception {
        LoginUserContext.setUserId("user-001");
        when(adminService.isCurrentUserAdmin()).thenReturn(false);

        mockMvc().perform(get("/internal/v1/places").param("keyword", "故宫"))
                .andExpect(status().isForbidden());

        verify(placeContentService, never()).searchPlacesForAdmin(any(PlaceAdminSearchQuery.class));
    }

    @Test
    void search_withoutAuthenticatedUser_isUnauthorized() throws Exception {
        mockMvc().perform(get("/internal/v1/places").param("keyword", "故宫"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void poiSuggestions_adminPassesKeywordAndCityToService() throws Exception {
        LoginUserContext.setUserId("admin-001");
        when(adminService.isCurrentUserAdmin()).thenReturn(true);
        when(placeAdminResolveService.listPoiSuggestions("故宫", "北京")).thenReturn(List.of());

        mockMvc().perform(get("/internal/v1/places/poi-suggestions")
                        .param("keyword", "故宫")
                        .param("city", "北京"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(placeAdminResolveService).listPoiSuggestions("故宫", "北京");
    }

    @Test
    void poiSuggestions_nonAdmin_isForbidden() throws Exception {
        LoginUserContext.setUserId("user-001");
        when(adminService.isCurrentUserAdmin()).thenReturn(false);

        mockMvc().perform(get("/internal/v1/places/poi-suggestions").param("keyword", "故宫"))
                .andExpect(status().isForbidden());

        verify(placeAdminResolveService, never()).listPoiSuggestions(anyString(), anyString());
    }

    @Test
    void resolve_adminPassesRequestBodyToService() throws Exception {
        LoginUserContext.setUserId("admin-001");
        when(adminService.isCurrentUserAdmin()).thenReturn(true);
        PlaceResolveFromPoiRequest request = new PlaceResolveFromPoiRequest(
                "B000A816R6", "故宫博物院", "北京市东城区景山前街4号", "北京市",
                null, null, "110101", "010", null);
        PlaceAdminSearchItem item = new PlaceAdminSearchItem(
                "place-100", "故宫博物院", "POI", "ATTRACTION", "AMAP", "ACTIVE", null,
                "北京市东城区景山前街4号", "北京市", "中国", null, null,
                List.of(), false, null, null, 0);
        when(placeAdminResolveService.resolvePlaceFromPoi(any(PlaceResolveFromPoiRequest.class)))
                .thenReturn(item);

        mockMvc().perform(post("/internal/v1/places/resolve-from-poi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"poiId":"B000A816R6","name":"故宫博物院",
                                 "address":"北京市东城区景山前街4号","cityName":"北京市",
                                 "adcode":"110101","cityCode":"010"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("place-100"))
                .andExpect(jsonPath("$.data.hasPublishedContent").value(false));

        verify(placeAdminResolveService).resolvePlaceFromPoi(request);
    }

    @Test
    void resolve_missingPoiId_isRejectedBeforeServiceCall() throws Exception {
        LoginUserContext.setUserId("admin-001");

        mockMvc().perform(post("/internal/v1/places/resolve-from-poi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"故宫博物院\"}"))
                .andExpect(status().isUnprocessableEntity());

        verify(placeAdminResolveService, never()).resolvePlaceFromPoi(any(PlaceResolveFromPoiRequest.class));
    }

    @Test
    void createPlace_adminPassesBodyToService() throws Exception {
        LoginUserContext.setUserId("admin-001");
        when(adminService.isCurrentUserAdmin()).thenReturn(true);
        PlaceAdminSearchItem item = new PlaceAdminSearchItem(
                "city-1", "京都", "DESTINATION", "CITY", "MANUAL", "ACTIVE", null,
                null, "京都", "日本", null, null, List.of(), false, null, null, 0);
        when(placeAdminResolveService.createPlace(any())).thenReturn(item);

        mockMvc().perform(post("/internal/v1/places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"placeScope":"DESTINATION","category":"CITY","name":"京都","country":"日本"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("city-1"))
                .andExpect(jsonPath("$.data.placeScope").value("DESTINATION"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new PlaceAdminSearchController(
                        placeContentService, placeAdminResolveService, adminService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }
}
