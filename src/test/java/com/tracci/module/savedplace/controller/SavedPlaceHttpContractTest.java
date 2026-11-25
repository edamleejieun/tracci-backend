package com.tracci.module.savedplace.controller;

import com.tracci.common.context.LoginUserContext;
import com.tracci.module.savedplace.dto.request.CreateSavedPlaceRequest;
import com.tracci.module.savedplace.service.SavedPlaceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SavedPlaceHttpContractTest {

    @Mock
    private SavedPlaceService savedPlaceService;

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void create_acceptsExistingPlaceIdWithoutLocation() throws Exception {
        MockMvc mockMvc = mockMvc();
        LoginUserContext.setUserId("u-1");

        mockMvc.perform(post("/api/v1/saved-places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "placeId": "place-1",
                                  "note": "详情页收藏"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<CreateSavedPlaceRequest> captor =
                ArgumentCaptor.forClass(CreateSavedPlaceRequest.class);
        verify(savedPlaceService).createSavedPlace(eq("u-1"), captor.capture());
        assertEquals("place-1", captor.getValue().placeId());
        assertNull(captor.getValue().location());
    }

    @Test
    void create_mapsLocationSearchPoiIdAndSourceAliases() throws Exception {
        MockMvc mockMvc = mockMvc();
        LoginUserContext.setUserId("u-1");

        mockMvc.perform(post("/api/v1/saved-places")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "placeScope": "POI",
                                  "category": "ATTRACTION",
                                  "location": {
                                    "poiId": "B000A8UIN8",
                                    "name": "浅草寺",
                                    "source": "AMAP"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<CreateSavedPlaceRequest> captor =
                ArgumentCaptor.forClass(CreateSavedPlaceRequest.class);
        verify(savedPlaceService).createSavedPlace(eq("u-1"), captor.capture());
        assertEquals("B000A8UIN8", captor.getValue().location().providerPlaceId());
        assertEquals("AMAP", captor.getValue().location().provider());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
                .standaloneSetup(new SavedPlaceController(savedPlaceService))
                .build();
    }
}
