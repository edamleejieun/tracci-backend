package com.tracci.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.exception.ThirdPartyException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.CosClient;
import com.tracci.integration.cos.CosPostUploadCredential;
import com.tracci.integration.cos.CosProperties;
import com.tracci.integration.wechat.WechatContentSecurityClient;
import com.tracci.module.auth.entity.UserAuthIdentity;
import com.tracci.module.auth.enums.AuthProvider;
import com.tracci.module.auth.repository.UserAuthIdentityMapper;
import com.tracci.module.file.dto.request.ConfirmUploadReq;
import com.tracci.module.file.dto.request.UploadCredentialReq;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.journal.entity.JournalPhoto;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.service.TripAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceImplTest {

    @Mock
    private FileMapper fileMapper;
    @Mock
    private JournalPhotoMapper journalPhotoMapper;
    @Mock
    private TripMapper tripMapper;
    @Mock
    private TripAccessService tripAccessService;
    @Mock
    private CosClient cosClient;
    @Mock
    private CosProperties cosProperties;
    @Mock
    private CiUrlBuilder ciUrlBuilder;
    @Mock
    private UserAuthIdentityMapper authIdentityMapper;
    @Mock
    private WechatContentSecurityClient wechatContentSecurityClient;

    @InjectMocks
    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        when(cosProperties.getBucket()).thenReturn("test-bucket");
        when(cosProperties.getRegion()).thenReturn("ap-guangzhou");
        when(cosProperties.getUploadUrlExpireSeconds()).thenReturn(600);
        when(cosProperties.getAccessUrlExpireSeconds()).thenReturn(1800);
        when(cosProperties.getMaxImageSizeBytes()).thenReturn(5_242_880L);
        when(cosProperties.getAllowedImageTypes()).thenReturn(List.of(
                "image/jpeg",
                "image/png",
                "image/webp",
                "image/avif",
                "image/gif",
                "image/bmp",
                "image/tiff",
                "image/heic",
                "image/heif"));
        when(cosProperties.getPublicBaseUrl()).thenReturn("");
        when(cosClient.generateUploadHeaders(anyString())).thenReturn(Map.of("Content-Type", "image/jpeg"));
        when(ciUrlBuilder.build(any(), anyString())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return CiUrlBuilder.CiUrls.mirror(url);
        });
        when(ciUrlBuilder.buildSigned(anyString(), anyString(), any())).thenAnswer(inv -> {
            String url = inv.getArgument(0);
            return CiUrlBuilder.CiUrls.mirror(url);
        });
    }

    // ---- 1. 创建上传凭证成功 ----

    @Test
    void createUploadCredential_success() {
        when(cosClient.generatePresignedUploadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("https://test-bucket.cos.ap-guangzhou.myqcloud.com/users/u-1/journal/2026/07/xxx.jpg?sign=xxx");
        doAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId("file-1");
            return 1;
        }).when(fileMapper).insert(any(File.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.createUploadCredential(new UploadCredentialReq(
                    "JOURNAL_PHOTO", null, "kyoto.jpg", "image/jpeg", 382000, "PUT"
            ));

            assertEquals("file-1", resp.fileId());
            assertTrue(resp.objectKey().contains("users/u-1/journal_photo/"));
            assertTrue(resp.uploadUrl().contains("sign=xxx"));
            assertEquals("PUT", resp.method());
            assertEquals("image/jpeg", resp.headers().get("Content-Type"));
            assertNotNull(resp.expiresAt());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileMapper).insert(captor.capture());
        File saved = captor.getValue();
        assertEquals("u-1", saved.getUserId());
        assertEquals("JOURNAL_PHOTO", saved.getBusinessType());
        assertEquals("PENDING", saved.getStatus());
        assertEquals("kyoto.jpg", saved.getOriginalFileName());
        assertEquals("image/jpeg", saved.getContentType());
        assertEquals(382000L, saved.getSize());
    }

    // ---- 2. 文件名为空失败 ----

    @Test
    void createUploadCredential_rejectsEmptyFileName() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "   ", "image/jpeg", 1000)));
            verify(fileMapper, never()).insert(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 3. contentType 非图片失败 ----

    @Test
    void createUploadCredential_rejectsNonImageContentType() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "file.pdf", "application/pdf", 1000)));
            verify(fileMapper, never()).insert(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createUploadCredential_supportsAvifImage() {
        when(cosClient.generatePresignedUploadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("https://upload-url");
        when(cosClient.generateUploadHeaders("image/avif")).thenReturn(Map.of("Content-Type", "image/avif"));
        doAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId("file-1");
            return 1;
        }).when(fileMapper).insert(any(File.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "photo.avif", "image/avif", 1000, "PUT"));

            assertTrue(resp.objectKey().endsWith(".avif"));
            assertEquals("image/avif", resp.headers().get("Content-Type"));
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileMapper).insert(captor.capture());
        assertEquals("image/avif", captor.getValue().getContentType());
    }

    // ---- 4. 文件大小为 0 失败 ----

    @Test
    void createUploadCredential_rejectsZeroSize() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "photo.jpg", "image/jpeg", 0)));
            verify(fileMapper, never()).insert(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 5. 文件大小超过限制失败 ----

    @Test
    void createUploadCredential_rejectsOversizedFile() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "photo.jpg", "image/jpeg", 6_000_000)));
            verify(fileMapper, never()).insert(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 6. objectKey 由后端生成 ----

    @Test
    void createUploadCredential_generatesObjectKey() {
        when(cosClient.generatePresignedUploadUrl(anyString(), anyString(), anyInt()))
                .thenReturn("https://upload-url");
        doAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId("file-1");
            return 1;
        }).when(fileMapper).insert(any(File.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "photo.jpg", "image/jpeg", 1000, "PUT"));

            assertTrue(resp.objectKey().startsWith("users/u-1/journal_photo/"));
            assertTrue(resp.objectKey().endsWith(".jpg"));
            // 确保没有路径穿越
            assertTrue(!resp.objectKey().contains(".."));
            assertTrue(!resp.objectKey().contains("//"));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createUploadCredential_validatesTripBusinessOwnership() {
        when(tripAccessService.requirePermission("trip-1", "u-1", TripPermission.EDIT_CONTENT))
                .thenReturn(new TripAccessService.TripAccess(new Trip(), null));
        when(cosClient.generatePostUploadCredential(anyString(), anyString(), any(Long.class), anyInt()))
                .thenReturn(new CosPostUploadCredential(
                        "https://test-bucket.cos.ap-guangzhou.myqcloud.com",
                        Map.of(
                                "key", "users/u-1/journal_photo/2026/07/xxx.jpg",
                                "policy", "mock-policy",
                                "q-signature", "mock-signature",
                                "Content-Type", "image/jpeg"
                        ),
                        "file"));
        doAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId("file-1");
            return 1;
        }).when(fileMapper).insert(any(File.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", "trip-1", "photo.jpg", "image/jpeg", 1000));

            assertEquals("file-1", resp.fileId());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileMapper).insert(captor.capture());
        assertEquals("trip-1", captor.getValue().getBusinessId());
    }

    @Test
    void createUploadCredential_rejectsOtherUserTripBusinessId() {
        when(tripAccessService.requirePermission("trip-1", "u-1", TripPermission.UPDATE_TRIP))
                .thenThrow(new ForbiddenException());

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("TRIP_COVER", "trip-1", "cover.jpg", "image/jpeg", 1000)));
            verify(fileMapper, never()).insert(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createUploadCredential_supportsPostObjectUpload() {
        when(cosClient.generatePostUploadCredential(anyString(), anyString(), any(Long.class), anyInt()))
                .thenReturn(new CosPostUploadCredential(
                        "https://test-bucket.cos.ap-guangzhou.myqcloud.com",
                        Map.of(
                                "key", "users/u-1/journal_photo/2026/07/xxx.jpg",
                                "policy", "mock-policy",
                                "q-signature", "mock-signature",
                                "Content-Type", "image/jpeg"
                        ),
                        "file"));
        doAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId("file-1");
            return 1;
        }).when(fileMapper).insert(any(File.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "photo.jpg", "image/jpeg", 1000, "POST"));

            assertEquals("POST", resp.method());
            assertEquals("https://test-bucket.cos.ap-guangzhou.myqcloud.com", resp.uploadUrl());
            assertEquals("file", resp.fileFieldName());
            assertEquals("mock-policy", resp.formData().get("policy"));
            assertNull(resp.headers());
        } finally {
            LoginUserContext.clear();
        }

        verify(cosClient).generatePostUploadCredential(anyString(), anyString(), any(Long.class), anyInt());
        verify(cosClient, never()).generatePresignedUploadUrl(anyString(), anyString(), anyInt());
    }

    @Test
    void createUploadCredential_defaultsToPostObjectUpload() {
        when(cosClient.generatePostUploadCredential(anyString(), anyString(), any(Long.class), anyInt()))
                .thenReturn(new CosPostUploadCredential(
                        "https://test-bucket.cos.ap-guangzhou.myqcloud.com",
                        Map.of(
                                "key", "users/u-1/journal_photo/2026/07/xxx.jpg",
                                "policy", "mock-policy",
                                "q-signature", "mock-signature",
                                "Content-Type", "image/jpeg"
                        ),
                        "file"));
        doAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId("file-1");
            return 1;
        }).when(fileMapper).insert(any(File.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "photo.jpg", "image/jpeg", 1000));

            assertEquals("POST", resp.method());
            assertEquals("file", resp.fileFieldName());
            assertEquals("mock-policy", resp.formData().get("policy"));
        } finally {
            LoginUserContext.clear();
        }

        verify(cosClient).generatePostUploadCredential(anyString(), anyString(), any(Long.class), anyInt());
        verify(cosClient, never()).generatePresignedUploadUrl(anyString(), anyString(), anyInt());
    }

    @Test
    void createUploadCredential_rejectsInvalidUploadMethod() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "photo.jpg", "image/jpeg", 1000, "PATCH")));
            verify(fileMapper, never()).insert(any(File.class));
            verify(cosClient, never()).generatePresignedUploadUrl(anyString(), anyString(), anyInt());
            verify(cosClient, never()).generatePostUploadCredential(anyString(), anyString(), any(Long.class), anyInt());
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 7. confirm 自己的文件成功 ----

    @Test
    void confirmUpload_success() {
        File file = pendingFile("file-1", "u-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);
        when(fileMapper.updateById(any(File.class))).thenReturn(1);
        when(cosClient.generatePresignedAccessUrl(file.getObjectKey(), 1800))
                .thenReturn("https://test-bucket.cos.ap-guangzhou.myqcloud.com/"
                        + file.getObjectKey() + "?sign=xxx");

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.confirmUpload("file-1",
                    new ConfirmUploadReq("\"etag123\"", 1080, 1440));

            assertEquals("file-1", resp.id());
            assertEquals("UPLOADED", resp.status());
            assertEquals(1080, resp.width());
            assertEquals(1440, resp.height());
            assertEquals("https://test-bucket.cos.ap-guangzhou.myqcloud.com/"
                    + file.getObjectKey() + "?sign=xxx", resp.url());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileMapper).updateById(captor.capture());
        assertEquals("UPLOADED", captor.getValue().getStatus());
        assertEquals("\"etag123\"", captor.getValue().getEtag());
        assertNull(captor.getValue().getUrl());
    }

    @Test
    void confirmUpload_rejectsWechatMiniRiskyImageBeforeUploaded() {
        File file = pendingFile("file-1", "u-1");
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setProvider(AuthProvider.WECHAT_MINI.name());
        identity.setProviderUserId("openid-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);
        when(authIdentityMapper.selectOne(any())).thenReturn(identity);
        byte[] imageBytes = "image-bytes".getBytes();
        when(cosClient.getObjectBytes(file.getObjectKey())).thenReturn(imageBytes);
        doThrow(new BizException(ErrorCode.CONTENT_SECURITY_REJECTED))
                .when(wechatContentSecurityClient).checkImage(eq("openid-1"), eq(imageBytes));

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> fileService.confirmUpload("file-1",
                    new ConfirmUploadReq("\"etag123\"", 1080, 1440)));

            assertEquals(ErrorCode.CONTENT_SECURITY_REJECTED, ex.getErrorCode());
            assertEquals("PENDING", file.getStatus());
            verify(fileMapper, never()).updateById(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void confirmUpload_rejectsWhenImageBytesUnavailableBeforeUploaded() {
        File file = pendingFile("file-1", "u-1");
        UserAuthIdentity identity = new UserAuthIdentity();
        identity.setProvider(AuthProvider.WECHAT_MINI.name());
        identity.setProviderUserId("openid-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);
        when(authIdentityMapper.selectOne(any())).thenReturn(identity);
        when(cosClient.getObjectBytes(file.getObjectKey()))
                .thenThrow(new ThirdPartyException(ErrorCode.CONTENT_SECURITY_UNAVAILABLE));

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> fileService.confirmUpload("file-1",
                    new ConfirmUploadReq("\"etag123\"", 1080, 1440)));

            assertEquals(ErrorCode.CONTENT_SECURITY_UNAVAILABLE, ex.getErrorCode());
            assertEquals("PENDING", file.getStatus());
            verify(fileMapper, never()).updateById(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void confirmUpload_returnsPublicUrlWhenConfigured() {
        when(cosProperties.getPublicBaseUrl()).thenReturn("https://cdn.example.com/");
        File file = pendingFile("file-1", "u-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);
        when(fileMapper.updateById(any(File.class))).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.confirmUpload("file-1",
                    new ConfirmUploadReq("\"etag123\"", 1080, 1440));

            assertEquals("https://cdn.example.com/" + file.getObjectKey(), resp.url());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileMapper).updateById(captor.capture());
        assertEquals("https://cdn.example.com/" + file.getObjectKey(), captor.getValue().getUrl());
        verify(cosClient, never()).generatePresignedAccessUrl(anyString(), anyInt());
    }

    // ---- 8. confirm 他人的文件失败 ----

    @Test
    void confirmUpload_rejectsOtherUserFile() {
        File file = pendingFile("file-1", "u-2");
        when(fileMapper.selectById("file-1")).thenReturn(file);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class,
                    () -> fileService.confirmUpload("file-1", new ConfirmUploadReq(null, null, null)));
            verify(fileMapper, never()).updateById(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 9. confirm 非 PENDING 文件失败 ----

    @Test
    void confirmUpload_rejectsAlreadyUploadedFile() {
        File file = uploadedFile("file-1", "u-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class,
                    () -> fileService.confirmUpload("file-1", new ConfirmUploadReq(null, null, null)));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 10. access-url 自己的文件成功 ----

    @Test
    void getAccessUrl_returnsPublicUrlWhenConfigured() {
        when(cosProperties.getPublicBaseUrl()).thenReturn("https://cdn.example.com");
        File file = uploadedFile("file-1", "u-1");
        file.setUrl("https://cdn.example.com/users/u-1/journal_photo/2026/07/xxx.jpg");
        when(fileMapper.selectById("file-1")).thenReturn(file);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.getAccessUrl("file-1");

            assertEquals("file-1", resp.fileId());
            assertEquals("https://cdn.example.com/users/u-1/journal_photo/2026/07/xxx.jpg", resp.url());
            assertNull(resp.expiresAt());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getAccessUrl_generatesPresignedWhenNoPublicUrl() {
        when(cosProperties.getPublicBaseUrl()).thenReturn("");
        File file = uploadedFile("file-1", "u-1");
        file.setUrl(null);
        when(fileMapper.selectById("file-1")).thenReturn(file);
        when(cosClient.generatePresignedAccessUrl(anyString(), anyInt()))
                .thenReturn("https://test-bucket.cos.ap-guangzhou.myqcloud.com/obj?sign=xxx");

        LoginUserContext.setUserId("u-1");
        try {
            var resp = fileService.getAccessUrl("file-1");

            assertEquals("file-1", resp.fileId());
            assertTrue(resp.url().contains("sign=xxx"));
            assertNotNull(resp.expiresAt());
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 11. access-url 他人的文件失败 ----

    @Test
    void getAccessUrl_rejectsOtherUserFile() {
        File file = uploadedFile("file-1", "u-2");
        when(fileMapper.selectById("file-1")).thenReturn(file);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class,
                    () -> fileService.getAccessUrl("file-1"));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 12. 删除自己的未引用文件成功 ----

    @Test
    void deleteFile_success() {
        File file = uploadedFile("file-1", "u-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);
        when(journalPhotoMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());
        when(fileMapper.updateById(any(File.class))).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            fileService.deleteFile("file-1");
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileMapper).updateById(captor.capture());
        assertEquals("DELETED", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    // ---- 13. 删除他人的文件失败 ----

    @Test
    void deleteFile_rejectsOtherUserFile() {
        File file = uploadedFile("file-1", "u-2");
        when(fileMapper.selectById("file-1")).thenReturn(file);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> fileService.deleteFile("file-1"));
            verify(fileMapper, never()).updateById(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- 14. 删除已被 journal_photos 引用的文件失败 ----

    @Test
    void deleteFile_rejectsReferencedFile() {
        File file = uploadedFile("file-1", "u-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);
        when(journalPhotoMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(new JournalPhoto()));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.deleteFile("file-1"));
            verify(fileMapper, never()).updateById(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void deleteFile_rejectsAlreadyDeletedFile() {
        File file = uploadedFile("file-1", "u-1");
        file.setStatus("DELETED");
        when(fileMapper.selectById("file-1")).thenReturn(file);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(NotFoundException.class, () -> fileService.deleteFile("file-1"));
            verify(fileMapper, never()).updateById(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createUploadCredential_rejectsMismatchedExtension() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "photo.png", "image/jpeg", 1000)));
            verify(fileMapper, never()).insert(any(File.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createUploadCredential_rejectsInvalidBusinessType() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("INVALID", null, "photo.jpg", "image/jpeg", 1000)));
            verify(fileMapper, never()).insert(any(File.class));
            verify(cosClient, never()).generatePresignedUploadUrl(anyString(), anyString(), anyInt());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createUploadCredential_rejectsPathLikeFileName() {
        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "../photo.jpg", "image/jpeg", 1000)));
            assertThrows(BizException.class, () -> fileService.createUploadCredential(
                    new UploadCredentialReq("JOURNAL_PHOTO", null, "dir/photo.jpg", "image/jpeg", 1000)));
            verify(fileMapper, never()).insert(any(File.class));
            verify(cosClient, never()).generatePresignedUploadUrl(anyString(), anyString(), anyInt());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getAccessUrl_rejectsPendingFile() {
        File file = pendingFile("file-1", "u-1");
        when(fileMapper.selectById("file-1")).thenReturn(file);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> fileService.getAccessUrl("file-1"));
        } finally {
            LoginUserContext.clear();
        }
    }

    // ---- helpers ----

    private File pendingFile(String id, String userId) {
        File file = new File();
        file.setId(id);
        file.setUserId(userId);
        file.setBusinessType("JOURNAL_PHOTO");
        file.setOriginalFileName("photo.jpg");
        file.setContentType("image/jpeg");
        file.setSize(100000L);
        file.setObjectKey("users/" + userId + "/journal_photo/2026/07/" + id + ".jpg");
        file.setBucket("test-bucket");
        file.setRegion("ap-guangzhou");
        file.setStatus("PENDING");
        file.setCreatedAt(LocalDateTime.of(2026, 7, 1, 10, 0));
        return file;
    }

    private File uploadedFile(String id, String userId) {
        File file = pendingFile(id, userId);
        file.setStatus("UPLOADED");
        file.setUrl("https://cdn.example.com/" + file.getObjectKey());
        return file;
    }
}
