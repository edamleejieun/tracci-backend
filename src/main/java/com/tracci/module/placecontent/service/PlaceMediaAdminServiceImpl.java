package com.tracci.module.placecontent.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.CosClient;
import com.tracci.integration.cos.CosPostUploadCredential;
import com.tracci.integration.cos.CosProperties;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.file.dto.request.ConfirmUploadReq;
import com.tracci.module.file.dto.request.UploadCredentialReq;
import com.tracci.module.file.dto.response.UploadCredentialResp;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.placecontent.dto.request.PlaceMediaUpdateRequest;
import com.tracci.module.placecontent.dto.response.PlaceMediaAdminItem;
import com.tracci.module.placecontent.entity.PlaceMediaAsset;
import com.tracci.module.placecontent.enums.MediaSourceType;
import com.tracci.module.placecontent.enums.MediaStatus;
import com.tracci.module.placecontent.repository.PlaceMediaAssetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlaceMediaAdminServiceImpl implements PlaceMediaAdminService {

    static final String BUSINESS_TYPE = "PLACE_MEDIA";

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

    private final PlaceService placeService;
    private final PlaceMediaAssetMapper mediaAssetMapper;
    private final FileMapper fileMapper;
    private final CosClient cosClient;
    private final CosProperties cosProperties;
    private final FileUrlResolver fileUrlResolver;

    @Override
    @Transactional
    public UploadCredentialResp createUploadCredential(String placeId, UploadCredentialReq request) {
        Place place = placeService.getPlaceById(placeId);
        String userId = LoginUserContext.requireUserId();
        String contentType = normalizeContentType(request.contentType());
        String fileName = requireFileName(request.fileName());
        validateFileExtension(fileName, contentType);
        if (request.size() <= 0) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "文件大小必须大于 0");
        }
        if (request.size() > cosProperties.getMaxImageSizeBytes()) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "文件大小不能超过 " + (cosProperties.getMaxImageSizeBytes() / 1024 / 1024) + " MB");
        }

        String objectKey = generateObjectKey(place.getId(), fileName);
        int expireSeconds = cosProperties.getUploadUrlExpireSeconds();
        CosPostUploadCredential postCredential = cosClient.generatePostUploadCredential(
                objectKey,
                contentType,
                cosProperties.getMaxImageSizeBytes(),
                expireSeconds);

        File file = new File();
        file.setUserId(userId);
        file.setBusinessType(BUSINESS_TYPE);
        file.setBusinessId(place.getId());
        file.setOriginalFileName(fileName);
        file.setContentType(contentType);
        file.setSize(request.size());
        file.setObjectKey(objectKey);
        file.setBucket(cosProperties.getBucket());
        file.setRegion(cosProperties.getRegion());
        file.setStatus("PENDING");
        file.setCreatedAt(DateTimeUtil.nowUtc());
        file.setUpdatedAt(DateTimeUtil.nowUtc());
        fileMapper.insert(file);

        LocalDateTime expiresAt = DateTimeUtil.nowUtc().plusSeconds(expireSeconds);
        return new UploadCredentialResp(
                file.getId(),
                objectKey,
                postCredential.uploadUrl(),
                "POST",
                null,
                postCredential.formData(),
                postCredential.fileFieldName(),
                DateTimeUtil.toInstantString(expiresAt));
    }

    @Override
    @Transactional
    public PlaceMediaAdminItem confirmUpload(String placeId, String fileId, ConfirmUploadReq request) {
        Place place = placeService.getPlaceById(placeId);
        File file = fileMapper.selectById(fileId);
        if (file == null || "DELETED".equals(file.getStatus())) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND);
        }
        if (!BUSINESS_TYPE.equals(file.getBusinessType()) || !place.getId().equals(file.getBusinessId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "文件不属于该地点");
        }

        if (!cosClient.objectExists(file.getObjectKey())) {
            throw new BizException(ErrorCode.FILE_NOT_UPLOADED, "文件尚未上传到对象存储");
        }

        if ("PENDING".equals(file.getStatus())) {
            file.setStatus("UPLOADED");
            if (request != null && StringUtils.hasText(request.etag())) {
                file.setEtag(request.etag().trim());
            }
            if (request != null) {
                file.setWidth(request.width());
                file.setHeight(request.height());
            }
            String publicBaseUrl = cosProperties.getPublicBaseUrl();
            if (StringUtils.hasText(publicBaseUrl)) {
                String base = publicBaseUrl.endsWith("/")
                        ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                        : publicBaseUrl;
                file.setUrl(base + "/" + file.getObjectKey());
            }
            file.setUpdatedAt(DateTimeUtil.nowUtc());
            fileMapper.updateById(file);
        } else if (!"UPLOADED".equals(file.getStatus())) {
            throw new BizException(ErrorCode.FILE_NOT_UPLOADED, "文件状态不是 PENDING，无法确认上传");
        }

        PlaceMediaAsset existing = mediaAssetMapper.selectOne(new QueryWrapper<PlaceMediaAsset>()
                .eq("place_id", place.getId())
                .eq("file_id", file.getId())
                .isNull("deleted_at")
                .last("LIMIT 1"));
        if (existing != null) {
            return toItem(existing, file);
        }

        int nextOrder = nextSortOrder(place.getId());
        String operatorId = LoginUserContext.requireUserId();
        PlaceMediaAsset asset = new PlaceMediaAsset();
        asset.setId(UUID.randomUUID().toString());
        asset.setPlaceId(place.getId());
        asset.setFileId(file.getId());
        asset.setSourceType(MediaSourceType.OWNED.name());
        asset.setLicenseType("OWNED");
        asset.setAltText(stripExtension(file.getOriginalFileName()));
        asset.setWidth(file.getWidth());
        asset.setHeight(file.getHeight());
        asset.setSortOrder(nextOrder);
        asset.setStatus(MediaStatus.APPROVED.name());
        asset.setReviewedBy(operatorId);
        asset.setReviewedAt(DateTimeUtil.nowUtc());
        asset.setCreatedAt(DateTimeUtil.nowUtc());
        asset.setUpdatedAt(DateTimeUtil.nowUtc());
        mediaAssetMapper.insert(asset);
        return toItem(asset, file);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlaceMediaAdminItem> listMedia(String placeId) {
        placeService.getPlaceById(placeId);
        List<PlaceMediaAsset> assets = mediaAssetMapper.selectList(new QueryWrapper<PlaceMediaAsset>()
                .eq("place_id", placeId)
                .ne("status", MediaStatus.ARCHIVED.name())
                .isNull("deleted_at")
                .orderByAsc("sort_order")
                .orderByAsc("id"));
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }
        List<PlaceMediaAdminItem> items = new ArrayList<>(assets.size());
        for (PlaceMediaAsset asset : assets) {
            File file = StringUtils.hasText(asset.getFileId()) ? fileMapper.selectById(asset.getFileId()) : null;
            items.add(toItem(asset, file));
        }
        return items;
    }

    @Override
    @Transactional
    public PlaceMediaAdminItem updateMedia(String mediaId, PlaceMediaUpdateRequest request) {
        PlaceMediaAsset asset = requireAsset(mediaId);
        UpdateWrapper<PlaceMediaAsset> update = new UpdateWrapper<PlaceMediaAsset>()
                .eq("id", asset.getId())
                .isNull("deleted_at");
        if (request.altText() != null) {
            update.set("alt_text", blankToNull(request.altText()));
        }
        if (request.attribution() != null) {
            update.set("attribution", blankToNull(request.attribution()));
        }
        if (request.licenseType() != null) {
            update.set("license_type", blankToNull(request.licenseType()));
        }
        if (request.sortOrder() != null) {
            update.set("sort_order", request.sortOrder());
        }
        if (request.rightsExpiresAt() != null) {
            update.set("rights_expires_at", request.rightsExpiresAt());
        }
        update.set("updated_at", DateTimeUtil.nowUtc());
        mediaAssetMapper.update(null, update);
        return toItem(requireAsset(mediaId), fileMapper.selectById(asset.getFileId()));
    }

    @Override
    @Transactional
    public PlaceMediaAdminItem setCover(String mediaId) {
        PlaceMediaAsset cover = requireAsset(mediaId);
        if (!MediaStatus.APPROVED.name().equals(cover.getStatus())) {
            throw new BizException(ErrorCode.PLACE_MEDIA_RIGHTS_INVALID, "只有已审核媒体可以设为封面");
        }
        List<PlaceMediaAsset> assets = mediaAssetMapper.selectList(new QueryWrapper<PlaceMediaAsset>()
                .eq("place_id", cover.getPlaceId())
                .eq("status", MediaStatus.APPROVED.name())
                .isNull("deleted_at")
                .orderByAsc("sort_order")
                .orderByAsc("id"));
        int order = 1;
        for (PlaceMediaAsset asset : assets) {
            int next = asset.getId().equals(cover.getId()) ? 0 : order++;
            mediaAssetMapper.update(null, new UpdateWrapper<PlaceMediaAsset>()
                    .eq("id", asset.getId())
                    .set("sort_order", next)
                    .set("updated_at", DateTimeUtil.nowUtc()));
        }
        return toItem(requireAsset(mediaId), fileMapper.selectById(cover.getFileId()));
    }

    @Override
    @Transactional
    public void archiveMedia(String mediaId) {
        PlaceMediaAsset asset = requireAsset(mediaId);
        mediaAssetMapper.update(null, new UpdateWrapper<PlaceMediaAsset>()
                .eq("id", asset.getId())
                .set("status", MediaStatus.ARCHIVED.name())
                .set("updated_at", DateTimeUtil.nowUtc()));
    }

    private PlaceMediaAsset requireAsset(String mediaId) {
        PlaceMediaAsset asset = mediaAssetMapper.selectById(mediaId);
        if (asset == null || asset.getDeletedAt() != null) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND, "地点媒体不存在");
        }
        return asset;
    }

    private int nextSortOrder(String placeId) {
        List<PlaceMediaAsset> assets = mediaAssetMapper.selectList(new QueryWrapper<PlaceMediaAsset>()
                .eq("place_id", placeId)
                .isNull("deleted_at")
                .select("sort_order")
                .orderByDesc("sort_order")
                .last("LIMIT 1"));
        if (assets == null || assets.isEmpty() || assets.get(0).getSortOrder() == null) {
            return 0;
        }
        return assets.get(0).getSortOrder() + 1;
    }

    private PlaceMediaAdminItem toItem(PlaceMediaAsset asset, File file) {
        String url = null;
        String thumbnailUrl = null;
        String previewUrl = null;
        if (file != null && "UPLOADED".equals(file.getStatus())) {
            CiUrlBuilder.CiUrls urls = fileUrlResolver.resolveUrls(file);
            url = blankToNull(urls.url());
            thumbnailUrl = blankToNull(urls.thumbnailUrl());
            previewUrl = blankToNull(urls.previewUrl());
        }
        return new PlaceMediaAdminItem(
                asset.getId(),
                asset.getPlaceId(),
                asset.getFileId(),
                asset.getStatus(),
                asset.getSortOrder(),
                asset.getAltText(),
                asset.getAttribution(),
                asset.getLicenseType(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getRightsExpiresAt(),
                asset.getReviewedAt(),
                url,
                thumbnailUrl,
                previewUrl
        );
    }

    private String generateObjectKey(String placeId, String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        int dotIndex = lowerName.lastIndexOf('.');
        String extension = lowerName.substring(dotIndex + 1);
        String yearMonth = DateTimeUtil.nowUtc().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "places/" + placeId + "/media/" + yearMonth + "/" + uuid + "." + extension;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "contentType 不能为空");
        }
        String value = contentType.trim().toLowerCase(Locale.ROOT);
        List<String> allowed = cosProperties.getAllowedImageTypes();
        if (allowed == null || !allowed.contains(value)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "contentType 仅支持图片类型");
        }
        return value;
    }

    private String requireFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileName 不能为空");
        }
        String trimmed = fileName.trim();
        if (trimmed.length() > 255) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileName 长度不能超过 255");
        }
        return trimmed;
    }

    private void validateFileExtension(String fileName, String contentType) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.contains("/") || lowerName.contains("\\") || lowerName.contains("..")) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileName 包含非法字符");
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

    private static String stripExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return blankToNull(base);
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
