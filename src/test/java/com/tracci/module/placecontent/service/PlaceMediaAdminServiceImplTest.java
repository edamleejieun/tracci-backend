package com.tracci.module.placecontent.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.CosClient;
import com.tracci.integration.cos.CosPostUploadCredential;
import com.tracci.integration.cos.CosProperties;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.file.dto.request.ConfirmUploadReq;
import com.tracci.module.file.dto.request.UploadCredentialReq;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.placecontent.entity.PlaceMediaAsset;
import com.tracci.module.placecontent.repository.PlaceMediaAssetMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceMediaAdminServiceImplTest {

    @Mock
    private PlaceService placeService;
    @Mock
    private PlaceMediaAssetMapper mediaAssetMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private CosClient cosClient;
    @Mock
    private FileUrlResolver fileUrlResolver;

    private PlaceMediaAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        CosProperties properties = new CosProperties();
        properties.setMaxImageSizeBytes(5_242_880L);
        properties.setUploadUrlExpireSeconds(600);
        properties.setAllowedImageTypes(List.of("image/jpeg", "image/png"));
        properties.setPublicBaseUrl("https://cdn.example");
        properties.setBucket("bucket");
        properties.setRegion("ap-guangzhou");
        service = new PlaceMediaAdminServiceImpl(
                placeService, mediaAssetMapper, fileMapper, cosClient, properties, fileUrlResolver);
        LoginUserContext.setUserId("admin-1");
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void createUploadCredential_usesPlacesObjectKey() {
        when(placeService.getPlaceById("place-1")).thenReturn(place("place-1"));
        when(cosClient.generatePostUploadCredential(anyString(), anyString(), anyLong(), anyInt()))
                .thenReturn(new CosPostUploadCredential("https://cos.example", Map.of("key", "v"), "file"));
        doAnswer(inv -> {
            File file = inv.getArgument(0);
            file.setId("file-1");
            return 1;
        }).when(fileMapper).insert(any(File.class));

        var cred = service.createUploadCredential("place-1", new UploadCredentialReq(
                "PLACE_MEDIA", "place-1", "cover.jpg", "image/jpeg", 1024, "POST"));

        assertThat(cred.fileId()).isEqualTo("file-1");
        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        org.mockito.Mockito.verify(fileMapper).insert(captor.capture());
        assertThat(captor.getValue().getObjectKey()).startsWith("places/place-1/media/");
        assertThat(captor.getValue().getBusinessType()).isEqualTo("PLACE_MEDIA");
    }

    @Test
    void confirmUpload_createsApprovedMedia() {
        when(placeService.getPlaceById("place-1")).thenReturn(place("place-1"));
        File file = pendingFile("file-1", "place-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);
        when(cosClient.objectExists(file.getObjectKey())).thenReturn(true);
        when(mediaAssetMapper.selectOne(any())).thenReturn(null);
        when(mediaAssetMapper.selectList(any())).thenReturn(List.of());
        when(fileUrlResolver.resolveUrls(any(File.class)))
                .thenReturn(CiUrlBuilder.CiUrls.mirror("https://cdn.example/places/place-1/media/x.jpg"));

        var item = service.confirmUpload("place-1", "file-1", new ConfirmUploadReq(null, 1600, 900));

        assertThat(item.status()).isEqualTo("APPROVED");
        assertThat(item.placeId()).isEqualTo("place-1");
        assertThat(item.thumbnailUrl()).isEqualTo("https://cdn.example/places/place-1/media/x.jpg");
        ArgumentCaptor<PlaceMediaAsset> captor = ArgumentCaptor.forClass(PlaceMediaAsset.class);
        org.mockito.Mockito.verify(mediaAssetMapper).insert(captor.capture());
        assertThat(captor.getValue().getSortOrder()).isEqualTo(0);
        assertThat(file.getStatus()).isEqualTo("UPLOADED");
        assertThat(file.getUrl()).isEqualTo("https://cdn.example/" + file.getObjectKey());
    }

    @Test
    void confirmUpload_rejectsWhenObjectIsMissing() {
        when(placeService.getPlaceById("place-1")).thenReturn(place("place-1"));
        File file = pendingFile("file-1", "place-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);
        when(cosClient.objectExists(file.getObjectKey())).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.confirmUpload("place-1", "file-1", new ConfirmUploadReq(null, 1600, 900)))
                .isInstanceOf(com.tracci.common.exception.BizException.class)
                .extracting(ex -> ((com.tracci.common.exception.BizException) ex).getErrorCode())
                .isEqualTo(com.tracci.common.response.ErrorCode.FILE_NOT_UPLOADED);
    }

    private static Place place(String id) {
        Place place = new Place();
        place.setId(id);
        place.setStatus("ACTIVE");
        return place;
    }

    private static File pendingFile(String id, String placeId) {
        File file = new File();
        file.setId(id);
        file.setBusinessType("PLACE_MEDIA");
        file.setBusinessId(placeId);
        file.setStatus("PENDING");
        file.setObjectKey("places/" + placeId + "/media/2026/09/abc.jpg");
        file.setOriginalFileName("cover.jpg");
        file.setContentType("image/jpeg");
        return file;
    }
}
