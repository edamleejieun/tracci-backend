package com.tracci.module.file.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
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
import com.tracci.module.file.dto.response.AccessUrlResp;
import com.tracci.module.file.dto.response.FileResp;
import com.tracci.module.file.dto.response.UploadCredentialResp;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.journal.entity.JournalPhoto;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.service.TripAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {
    private static final Map<String, Set<String>> IMAGE_EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg", "jfif"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp"),
            "image/avif", Set.of("avif"),
            "image/gif", Set.of("gif"),
            "image/bmp", Set.of("bmp", "dib"),
            "image/tiff", Set.of("tif", "tiff"),
            "image/heic", Set.of("heic"),
            "image/heif", Set.of("heif"));

    private final FileMapper fileMapper;
    private final JournalPhotoMapper journalPhotoMapper;
    private final TripAccessService tripAccessService;
    private final CosClient cosClient;
    private final CosProperties cosProperties;
    private final CiUrlBuilder ciUrlBuilder;
    private final UserAuthIdentityMapper authIdentityMapper;
    private final WechatContentSecurityClient wechatContentSecurityClient;

    @Override
    @Transactional
    public UploadCredentialResp createUploadCredential(UploadCredentialReq req) {
        String userId = LoginUserContext.requireUserId();

        String contentType = normalizeContentType(req.contentType());
        String businessType = normalizeBusinessType(req.businessType());
        String businessId = normalizeOptional(req.businessId());
        validateBusinessOwnership(userId, businessType, businessId);
        String uploadMethod = normalizeUploadMethod(req.uploadMethod());
        String fileName = req.fileName().trim();
        if (!StringUtils.hasText(fileName)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileName 不能为空");
        }
        if (fileName.length() > 255) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileName 长度不能超过 255");
        }
        if (req.size() <= 0) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "文件大小必须大于 0");
        }
        if (req.size() > cosProperties.getMaxImageSizeBytes()) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "文件大小不能超过 " + (cosProperties.getMaxImageSizeBytes() / 1024 / 1024) + " MB");
        }
        validateFileExtension(fileName, contentType);

        String objectKey = generateObjectKey(userId, businessType, fileName);
        int expireSeconds = cosProperties.getUploadUrlExpireSeconds();
        String uploadUrl;
        Map<String, String> headers = null;
        Map<String, String> formData = null;
        String fileFieldName = null;
        if ("POST".equals(uploadMethod)) {
            CosPostUploadCredential postCredential = cosClient.generatePostUploadCredential(
                    objectKey,
                    contentType,
                    cosProperties.getMaxImageSizeBytes(),
                    expireSeconds);
            uploadUrl = postCredential.uploadUrl();
            formData = postCredential.formData();
            fileFieldName = postCredential.fileFieldName();
        } else {
            uploadUrl = cosClient.generatePresignedUploadUrl(objectKey, contentType, expireSeconds);
            headers = cosClient.generateUploadHeaders(contentType);
        }
        LocalDateTime expiresAt = DateTimeUtil.nowUtc().plusSeconds(expireSeconds);

        File file = new File();
        file.setUserId(userId);
        file.setBusinessType(businessType);
        file.setBusinessId(businessId);
        file.setOriginalFileName(fileName);
        file.setContentType(contentType);
        file.setSize(req.size());
        file.setObjectKey(objectKey);
        file.setBucket(cosProperties.getBucket());
        file.setRegion(cosProperties.getRegion());
        file.setStatus("PENDING");
        file.setCreatedAt(DateTimeUtil.nowUtc());
        file.setUpdatedAt(DateTimeUtil.nowUtc());
        fileMapper.insert(file);

        return new UploadCredentialResp(
                file.getId(),
                objectKey,
                uploadUrl,
                uploadMethod,
                headers,
                formData,
                fileFieldName,
                DateTimeUtil.toInstantString(expiresAt));
    }

    @Override
    @Transactional
    public FileResp confirmUpload(String fileId, ConfirmUploadReq req) {
        String userId = LoginUserContext.requireUserId();
        File file = getExistingFile(fileId);
        assertOwner(file, userId);

        if (!"PENDING".equals(file.getStatus())) {
            throw new BizException(ErrorCode.FILE_NOT_UPLOADED, "文件状态不是 PENDING，无法确认上传");
        }

        checkImageContentSafety(userId, file);

        file.setStatus("UPLOADED");
        if (req.etag() != null) {
            file.setEtag(req.etag().trim());
        }
        file.setWidth(req.width());
        file.setHeight(req.height());
        file.setUpdatedAt(DateTimeUtil.nowUtc());

        String publicBaseUrl = cosProperties.getPublicBaseUrl();
        if (StringUtils.hasText(publicBaseUrl)) {
            String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
            file.setUrl(base + "/" + file.getObjectKey());
        }

        fileMapper.updateById(file);
        return toResp(file, resolveConfirmResponseUrl(file));
    }

    @Override
    @Transactional(readOnly = true)
    public AccessUrlResp getAccessUrl(String fileId) {
        String userId = LoginUserContext.requireUserId();
        File file = getExistingFile(fileId);
        assertOwner(file, userId);

        if (!"UPLOADED".equals(file.getStatus())) {
            throw new BizException(ErrorCode.FILE_NOT_UPLOADED, "文件尚未上传完成，无法获取访问 URL");
        }

        String baseUrl;
        String expiresAtStr = null;
        if (StringUtils.hasText(file.getUrl())) {
            baseUrl = file.getUrl();
        } else {
            int expireSeconds = cosProperties.getAccessUrlExpireSeconds();
            baseUrl = cosClient.generatePresignedAccessUrl(file.getObjectKey(), expireSeconds);
            expiresAtStr = DateTimeUtil.toInstantString(DateTimeUtil.nowUtc().plusSeconds(expireSeconds));
        }

        CiUrlBuilder.CiUrls ciUrls = buildAccessUrls(file, baseUrl);
        return new AccessUrlResp(
                file.getId(),
                ciUrls.url(),
                ciUrls.thumbnailUrl(),
                ciUrls.previewUrl(),
                ciUrls.exportUrl(),
                ciUrls.storyPortraitUrl(),
                ciUrls.storyCardUrl(),
                ciUrls.squareUrl(),
                ciUrls.watermarkedUrl(),
                expiresAtStr);
    }

    @Override
    @Transactional
    public void deleteFile(String fileId) {
        String userId = LoginUserContext.requireUserId();
        File file = getExistingFile(fileId);
        assertOwner(file, userId);

        if ("DELETED".equals(file.getStatus())) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND);
        }

        List<JournalPhoto> refs = journalPhotoMapper.selectList(new QueryWrapper<JournalPhoto>()
                .eq("file_id", fileId));
        if (!refs.isEmpty()) {
            throw new BizException(ErrorCode.FILE_REFERENCED);
        }

        file.setStatus("DELETED");
        file.setUpdatedAt(DateTimeUtil.nowUtc());
        file.setDeletedAt(DateTimeUtil.nowUtc());
        fileMapper.updateById(file);
    }

    private File getExistingFile(String fileId) {
        File file = fileMapper.selectById(fileId);
        if (file == null || "DELETED".equals(file.getStatus())) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND);
        }
        return file;
    }

    private void assertOwner(File file, String userId) {
        if (!userId.equals(file.getUserId())) {
            throw new ForbiddenException();
        }
    }

    private void validateBusinessOwnership(String userId, String businessType, String businessId) {
        if (!StringUtils.hasText(businessId)) {
            return;
        }
        if (!List.of("JOURNAL_PHOTO", "TRIP_COVER").contains(businessType)) {
            return;
        }
        TripPermission permission = "TRIP_COVER".equals(businessType)
                ? TripPermission.UPDATE_TRIP
                : TripPermission.EDIT_CONTENT;
        tripAccessService.requirePermission(businessId, userId, permission);
    }

    private void checkImageContentSafety(String userId, File file) {
        String openid = findWechatMiniOpenid(userId);
        if (!StringUtils.hasText(openid)) {
            return;
        }
        byte[] imageBytes = cosClient.getObjectBytes(file.getObjectKey());
        wechatContentSecurityClient.checkImage(openid, imageBytes);
    }

    private String findWechatMiniOpenid(String userId) {
        UserAuthIdentity identity = authIdentityMapper.selectOne(new LambdaQueryWrapper<UserAuthIdentity>()
                .eq(UserAuthIdentity::getUserId, userId)
                .eq(UserAuthIdentity::getProvider, AuthProvider.WECHAT_MINI.name())
                .last("limit 1"));
        return identity != null ? identity.getProviderUserId() : null;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "contentType 不能为空");
        }
        String value = contentType.trim().toLowerCase(Locale.ROOT);
        if (!cosProperties.getAllowedImageTypes().contains(value)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "contentType 仅支持 " + String.join(", ", cosProperties.getAllowedImageTypes()));
        }
        return value;
    }

    private String normalizeBusinessType(String businessType) {
        if (!StringUtils.hasText(businessType)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "businessType 不能为空");
        }
        String value = businessType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("JOURNAL_PHOTO", "TRIP_COVER", "AVATAR", "FEEDBACK_ATTACHMENT").contains(value)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "businessType 仅支持 JOURNAL_PHOTO / TRIP_COVER / AVATAR / FEEDBACK_ATTACHMENT");
        }
        return value;
    }

    private String normalizeUploadMethod(String uploadMethod) {
        if (!StringUtils.hasText(uploadMethod)) {
            return "POST";
        }
        String value = uploadMethod.trim().toUpperCase(Locale.ROOT);
        if (!List.of("PUT", "POST").contains(value)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "uploadMethod 仅支持 PUT / POST");
        }
        return value;
    }

    private void validateFileExtension(String fileName, String contentType) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.contains("/") || lowerName.contains("\\") || lowerName.contains("..")) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileName 包含非法字符");
        }
        for (int i = 0; i < fileName.length(); i++) {
            if (Character.isISOControl(fileName.charAt(i))) {
                throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileName 包含非法字符");
            }
        }
        int dotIndex = lowerName.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileName 缺少后缀名");
        }
        String extension = lowerName.substring(dotIndex + 1);
        Set<String> allowedExtensions = IMAGE_EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        if (allowedExtensions == null || !allowedExtensions.contains(extension)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileName 后缀名与 contentType 不匹配");
        }
    }

    private String generateObjectKey(String userId, String businessType, String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        int dotIndex = lowerName.lastIndexOf('.');
        String extension = lowerName.substring(dotIndex + 1);
        String yearMonth = DateTimeUtil.nowUtc().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "users/" + userId + "/" + businessType.toLowerCase(Locale.ROOT) + "/" + yearMonth + "/" + uuid + "." + extension;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private FileResp toResp(File file, String baseUrl) {
        CiUrlBuilder.CiUrls ciUrls;
        if (StringUtils.hasText(file.getUrl())) {
            ciUrls = ciUrlBuilder.build(baseUrl, file.getContentType());
        } else {
            ciUrls = buildAccessUrls(file, baseUrl);
        }
        return new FileResp(
                file.getId(),
                ciUrls.url(),
                ciUrls.thumbnailUrl(),
                ciUrls.previewUrl(),
                ciUrls.exportUrl(),
                ciUrls.storyPortraitUrl(),
                ciUrls.storyCardUrl(),
                ciUrls.squareUrl(),
                ciUrls.watermarkedUrl(),
                file.getContentType(),
                file.getSize() != null ? file.getSize() : 0,
                file.getWidth(),
                file.getHeight(),
                file.getStatus(),
                DateTimeUtil.toInstantString(file.getCreatedAt()));
    }

    private String resolveConfirmResponseUrl(File file) {
        if (StringUtils.hasText(file.getUrl())) {
            return file.getUrl();
        }
        return cosClient.generatePresignedAccessUrl(file.getObjectKey(), cosProperties.getAccessUrlExpireSeconds());
    }

    private CiUrlBuilder.CiUrls buildAccessUrls(File file, String baseUrl) {
        if (StringUtils.hasText(file.getUrl())) {
            return ciUrlBuilder.build(baseUrl, file.getContentType());
        }
        int expireSeconds = cosProperties.getAccessUrlExpireSeconds();
        return ciUrlBuilder.buildSigned(
                baseUrl,
                file.getContentType(),
                rule -> cosClient.generatePresignedAccessUrl(file.getObjectKey(), expireSeconds, rule));
    }

}
