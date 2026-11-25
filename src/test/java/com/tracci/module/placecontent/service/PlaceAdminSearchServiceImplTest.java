package com.tracci.module.placecontent.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.entity.PlaceProviderRef;
import com.tracci.module.place.repository.PlaceMapper;
import com.tracci.module.place.repository.PlaceProviderRefMapper;
import com.tracci.module.placecontent.dto.request.PlaceAdminSearchQuery;
import com.tracci.module.placecontent.dto.response.PlaceAdminSearchItem;
import com.tracci.module.placecontent.entity.PlaceContentVersion;
import com.tracci.module.placecontent.repository.PlaceContentSourceMapper;
import com.tracci.module.placecontent.repository.PlaceContentVersionMapper;
import com.tracci.module.placecontent.repository.PlaceMediaAssetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端地点搜索的聚合逻辑测试：
 * Provider 引用分组、PUBLISHED 状态识别、空页短路与筛选参数校验。
 */
@ExtendWith(MockitoExtension.class)
class PlaceAdminSearchServiceImplTest {

    @Mock
    private PlaceContentVersionMapper versionMapper;

    @Mock
    private PlaceContentSourceMapper sourceMapper;

    @Mock
    private PlaceMediaAssetMapper mediaAssetMapper;

    @Mock
    private PlaceMapper placeMapper;

    @Mock
    private PlaceProviderRefMapper providerRefMapper;

    @Mock
    private FileMapper fileMapper;

    @Mock
    private FileUrlResolver fileUrlResolver;

    private PlaceContentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlaceContentServiceImpl(
                versionMapper, sourceMapper, mediaAssetMapper, placeMapper, providerRefMapper,
                fileMapper, fileUrlResolver);
    }

    @Test
    void search_aggregatesProviderRefsAndPublishedState() {
        Place place = place("place-001", "故宫博物院", "北京");
        when(placeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(placeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(place));
        when(providerRefMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                ref("place-001", "AMAP", "B000A816R6"),
                ref("place-other", "AMAP", "B0FF000000")));
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                version("version-003", "place-001", 3, "DRAFT"),
                version("version-002", "place-001", 2, "PUBLISHED")));

        PageResponse<PlaceAdminSearchItem> result = service.searchPlacesForAdmin(
                new PlaceAdminSearchQuery("故宫", null, null, null, null, null, 1, 20));

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.list()).hasSize(1);
        PlaceAdminSearchItem item = result.list().get(0);
        assertThat(item.id()).isEqualTo("place-001");
        assertThat(item.canonicalName()).isEqualTo("故宫博物院");
        assertThat(item.cityName()).isEqualTo("北京");
        // 只保留当前 Place 的 provider 引用，不串入其他地点
        assertThat(item.providerRefs()).hasSize(1);
        assertThat(item.providerRefs().get(0).provider()).isEqualTo("AMAP");
        assertThat(item.providerRefs().get(0).providerPlaceId()).isEqualTo("B000A816R6");
        assertThat(item.hasPublishedContent()).isTrue();
        assertThat(item.publishedVersionId()).isEqualTo("version-002");
        assertThat(item.publishedVersionNo()).isEqualTo(2);
        assertThat(item.contentVersionCount()).isEqualTo(2);
    }

    @Test
    void search_placeWithoutVersions_hasNoPublishedContent() {
        when(placeMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(placeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(place("place-002", "颐和园", "北京")));
        when(providerRefMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                version("version-001", "place-002", 1, "DRAFT")));

        PageResponse<PlaceAdminSearchItem> result = service.searchPlacesForAdmin(
                new PlaceAdminSearchQuery("颐和园", null, null, null, null, null, 1, 20));

        PlaceAdminSearchItem item = result.list().get(0);
        assertThat(item.hasPublishedContent()).isFalse();
        assertThat(item.publishedVersionId()).isNull();
        assertThat(item.publishedVersionNo()).isNull();
        assertThat(item.contentVersionCount()).isEqualTo(1);
    }

    @Test
    void search_emptyPage_skipsRefAndVersionQueries() {
        when(placeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(placeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        PageResponse<PlaceAdminSearchItem> result = service.searchPlacesForAdmin(
                new PlaceAdminSearchQuery("不存在的地点", null, null, null, null, null, 1, 20));

        assertThat(result.total()).isZero();
        assertThat(result.list()).isEmpty();
        verify(providerRefMapper, never()).selectList(any(Wrapper.class));
        verify(versionMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void search_keywordFlowsIntoQueryParameters() {
        when(placeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(placeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.searchPlacesForAdmin(
                new PlaceAdminSearchQuery("故宫博物院", null, "北京", null, null, null, 1, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Place>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(placeMapper).selectList(captor.capture());
        String sqlSegment = ((com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Place>) captor.getValue())
                .getSqlSegment();
        assertThat(sqlSegment)
                .contains("canonical_name LIKE", "address LIKE", "city_name LIKE")
                .doesNotContain("place_scope", "category", "EXISTS");
    }

    @Test
    void search_uuidLikeKeyword_alsoMatchesById() {
        when(placeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(placeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        String uuid = "3f2a8c10-1111-4c22-9abc-def012345678";

        service.searchPlacesForAdmin(new PlaceAdminSearchQuery(uuid, null, null, null, null, null, 1, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Place>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(placeMapper).selectList(captor.capture());
        String sqlSegment = ((com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Place>) captor.getValue())
                .getSqlSegment();
        assertThat(sqlSegment).contains("id =");
    }

    @Test
    void search_placeIdFilter_appliesExactMatch() {
        when(placeMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(placeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.searchPlacesForAdmin(
                new PlaceAdminSearchQuery(null, "place-100", null, null, null, null, 1, 20));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<Place>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(placeMapper).selectList(captor.capture());
        String sqlSegment = ((com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Place>) captor.getValue())
                .getSqlSegment();
        assertThat(sqlSegment).contains("id =").doesNotContain("LIKE");
    }

    @Test
    void search_invalidPlaceScope_isRejected() {
        assertThatThrownBy(() -> service.searchPlacesForAdmin(
                new PlaceAdminSearchQuery(null, null, null, "CITY_CENTER", null, null, 1, 20)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    @Test
    void search_invalidContentState_isRejected() {
        assertThatThrownBy(() -> service.searchPlacesForAdmin(
                new PlaceAdminSearchQuery(null, null, null, null, null, "DRAFT", 1, 20)))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PARAM_INVALID);
    }

    // ---- fixtures ----

    private Place place(String id, String name, String cityName) {
        Place place = new Place();
        place.setId(id);
        place.setCanonicalName(name);
        place.setCityName(cityName);
        place.setPlaceScope("POI");
        place.setCategory("ATTRACTION");
        place.setStatus("ACTIVE");
        return place;
    }

    private PlaceProviderRef ref(String placeId, String provider, String providerPlaceId) {
        PlaceProviderRef ref = new PlaceProviderRef();
        ref.setPlaceId(placeId);
        ref.setProvider(provider);
        ref.setProviderPlaceId(providerPlaceId);
        ref.setIsPrimary(true);
        ref.setLastCheckedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        return ref;
    }

    private PlaceContentVersion version(String id, String placeId, int versionNo, String status) {
        PlaceContentVersion version = new PlaceContentVersion();
        version.setId(id);
        version.setPlaceId(placeId);
        version.setVersionNo(versionNo);
        version.setStatus(status);
        version.setLocale("zh-CN");
        return version;
    }
}
