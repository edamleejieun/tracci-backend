package com.tracci.module.journal.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.common.enums.LocationProvider;
import com.tracci.module.journal.dto.request.JournalEntryCreateReq;
import com.tracci.module.journal.dto.request.JournalEntryLocationReq;
import com.tracci.module.journal.dto.request.JournalEntryUpdateReq;
import com.tracci.module.journal.dto.response.JournalEntryLocationResp;
import com.tracci.module.journal.dto.response.JournalEntryResp;
import com.tracci.module.journal.entity.JournalEntry;
import com.tracci.module.journal.entity.JournalPhoto;
import com.tracci.module.journal.enums.JournalMood;
import com.tracci.module.journal.repository.JournalEntryMapper;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.dto.response.TripActivityLogResp;
import com.tracci.module.trip.enums.TripStatus;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.service.TripAccessService;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.service.TripActivityLogService;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JournalEntryServiceImpl implements JournalEntryService {

    private static final int MAX_CONTENT_LENGTH = 5000;
    private static final int MAX_IMAGES = 9;
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/avif",
            "image/gif",
            "image/bmp",
            "image/tiff",
            "image/heic",
            "image/heif");

    private static final String VISIBILITY_ALL = "all";
    private static final String VISIBILITY_PRIVATE = "private";

    private final JournalEntryMapper journalEntryMapper;
    private final JournalPhotoMapper journalPhotoMapper;
    private final TripMapper tripMapper;
    private final FileMapper fileMapper;
    private final FileUrlResolver fileUrlResolver;
    private final TripAccessService tripAccessService;
    private final TripActivityLogService activityLogService;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public JournalEntryResp createEntry(String tripId, JournalEntryCreateReq req) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        requireTripContentMutable(access.trip());

        String content = normalizeOptional(req.content());
        List<File> validatedFiles = validateFileIds(req.fileIds(), userId, tripId);

        if (!StringUtils.hasText(content) && validatedFiles.isEmpty()) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "content 和 fileIds 不能同时为空");
        }

        LocalDateTime occurredAt = req.occurredAt();

        JournalEntry entry = new JournalEntry();
        entry.setTripId(tripId);
        entry.setUserId(userId);
        entry.setContent(content);
        applyLocationForCreate(entry, req.location());
        entry.setOccurredAt(occurredAt);
        if (req.mood() != null) {
            entry.setMood(normalizeOptionalMood(req.mood()));
        }
        entry.setIsHighlight(req.isHighlight() != null && req.isHighlight());
        entry.setVisibility(normalizeVisibility(req.visibility()));
        entry.setSortOrder(0);
        journalEntryMapper.insert(entry);

        List<JournalPhoto> photos = replacePhotos(entry, validatedFiles);

        // Only record activity for non-private entries
        if (!VISIBILITY_PRIVATE.equals(entry.getVisibility())) {
            recordActivity(tripId, userId, memberId(access), "JOURNAL_CREATED", "JOURNAL", entry.getId(),
                    changes(
                            change("contentLength", null, lengthString(entry.getContent())),
                            change("photoCount", null, String.valueOf(photos.size())),
                            change("locationName", null, entry.getLocationName()),
                            change("mood", null, entry.getMood()),
                            change("isHighlight", null, String.valueOf(Boolean.TRUE.equals(entry.getIsHighlight())))),
                    "创建旅行日志");
        }
        Map<String, File> filesById = validatedFiles.stream().collect(Collectors.toMap(File::getId, f -> f));
        String authorName = resolveAuthorName(userId);
        return toResp(entry, photos, filesById, authorName);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<JournalEntryResp> listEntries(String tripId, int page, int pageSize) {
        String userId = LoginUserContext.requireUserId();
        requireReadAccess(tripId, userId);

        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);

        QueryWrapper<JournalEntry> query = new QueryWrapper<JournalEntry>()
                .eq("trip_id", tripId)
                .and(w -> w.eq("visibility", VISIBILITY_ALL)
                        .or()
                        .eq("user_id", userId))
                .orderByAsc("occurred_at")
                .orderByAsc("sort_order");

        Page<JournalEntry> result = journalEntryMapper.selectPage(new Page<>(safePage, safePageSize), query);
        Map<String, List<JournalPhoto>> photosByEntryId = findPhotosByEntryIds(result.getRecords());
        Map<String, File> filesById = findFilesByPhotoEntries(photosByEntryId);
        Map<String, String> authorNames = resolveAuthorNames(result.getRecords());
        List<JournalEntryResp> list = result.getRecords().stream()
                .map(entry -> toResp(entry, photosByEntryId.getOrDefault(entry.getId(), List.of()), filesById,
                        authorNames.getOrDefault(entry.getUserId(), null)))
                .toList();
        return PageResponse.of(list, result.getTotal(), safePage, safePageSize);
    }

    @Override
    @Transactional
    public JournalEntryResp updateEntry(String tripId, String entryId, JournalEntryUpdateReq req) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        requireTripContentMutable(access.trip());

        JournalEntry entry = journalEntryMapper.selectOne(new QueryWrapper<JournalEntry>()
                .eq("id", entryId)
                .eq("trip_id", tripId));
        if (entry == null) {
            throw new NotFoundException(ErrorCode.JOURNAL_NOT_FOUND);
        }
        requireEntryOwnership(entry);

        String oldVisibility = entry.getVisibility();
        String oldContentLength = lengthString(entry.getContent());
        String oldLocationName = entry.getLocationName();
        String oldMood = entry.getMood();
        String oldHighlight = String.valueOf(Boolean.TRUE.equals(entry.getIsHighlight()));
        LocalDateTime oldOccurredAt = entry.getOccurredAt();
        int oldPhotoCount = findPhotosByEntryId(entryId, tripId).size();

        if (req.content() != null) {
            entry.setContent(normalizeOptional(req.content()));
        }
        if (req.location() != null) {
            applyLocationForUpdate(entry, req.location());
        }
        if (req.occurredAt() != null) {
            entry.setOccurredAt(req.occurredAt());
        }
        if (req.isHighlight() != null) {
            entry.setIsHighlight(req.isHighlight());
        }
        if (req.mood() != null) {
            entry.setMood(normalizeOptionalMood(req.mood()));
        }
        if (req.visibility() != null) {
            entry.setVisibility(normalizeVisibility(req.visibility()));
        }

        List<File> validatedFiles = null;
        boolean shouldReplacePhotos = req.fileIds() != null;
        if (shouldReplacePhotos) {
            validatedFiles = validateFileIds(req.fileIds(), userId, tripId);
        }

        String content = entry.getContent();
        List<JournalPhoto> currentPhotos = shouldReplacePhotos
                ? List.of()
                : findPhotosByEntryId(entryId, tripId);
        if (!StringUtils.hasText(content) && currentPhotos.isEmpty() && (validatedFiles == null || validatedFiles.isEmpty())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "content 和 fileIds 不能同时为空");
        }

        int updated = journalEntryMapper.updateById(entry);
        if (updated == 0) {
            throw new NotFoundException(ErrorCode.JOURNAL_NOT_FOUND);
        }

        List<JournalPhoto> photos;
        if (shouldReplacePhotos) {
            photos = replacePhotos(entry, validatedFiles);
        } else {
            photos = currentPhotos;
        }
        if (VISIBILITY_PRIVATE.equals(entry.getVisibility())) {
            journalPhotoMapper.clearFeaturedByJournalEntryId(entryId, tripId);
            photos.forEach(photo -> photo.setIsFeatured(false));
        }
        // Only record activity for non-private entries
        if (!VISIBILITY_PRIVATE.equals(entry.getVisibility())) {
            recordActivity(tripId, userId, memberId(access), "JOURNAL_UPDATED", "JOURNAL", entry.getId(),
                    changes(
                            change("contentLength", oldContentLength, lengthString(entry.getContent())),
                            change("photoCount", String.valueOf(oldPhotoCount), String.valueOf(photos.size())),
                            change("locationName", oldLocationName, entry.getLocationName()),
                            change("mood", oldMood, entry.getMood()),
                            change("isHighlight", oldHighlight, String.valueOf(Boolean.TRUE.equals(entry.getIsHighlight()))),
                            change("visibility", oldVisibility, entry.getVisibility()),
                            change("occurredAt", DateTimeUtil.toInstantString(oldOccurredAt),
                                    DateTimeUtil.toInstantString(entry.getOccurredAt()))),
                    "更新旅行日志");
        }
        Map<String, File> filesById = findFilesByPhotoList(photos);
        String authorName = resolveAuthorName(entry.getUserId());
        return toResp(entry, photos, filesById, authorName);
    }

    @Override
    @Transactional
    public JournalEntryResp.PhotoResp featurePhoto(String tripId, String photoId) {
        return updatePhotoFeatured(tripId, photoId, true);
    }

    @Override
    @Transactional
    public JournalEntryResp.PhotoResp unfeaturePhoto(String tripId, String photoId) {
        return updatePhotoFeatured(tripId, photoId, false);
    }

    @Override
    @Transactional
    public void deleteEntry(String tripId, String entryId) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        requireTripContentMutable(access.trip());

        JournalEntry entry = journalEntryMapper.selectOne(new QueryWrapper<JournalEntry>()
                .eq("id", entryId)
                .eq("trip_id", tripId));
        if (entry == null) {
            throw new NotFoundException(ErrorCode.JOURNAL_NOT_FOUND);
        }
        requireEntryOwnership(entry);

        int deleted = journalEntryMapper.delete(new UpdateWrapper<JournalEntry>()
                .eq("id", entryId)
                .eq("trip_id", tripId));
        if (deleted == 0) {
            throw new NotFoundException(ErrorCode.JOURNAL_NOT_FOUND);
        }
        journalPhotoMapper.softDeleteAllByJournalEntryId(entryId, tripId);
        journalPhotoMapper.softDeleteByJournalEntryId(entryId, tripId, userId);
        // Only record activity for non-private entries
        if (!VISIBILITY_PRIVATE.equals(entry.getVisibility())) {
            recordActivity(tripId, userId, memberId(access), "JOURNAL_DELETED", "JOURNAL", entryId,
                    List.of(), "删除旅行日志");
        }
    }

    private TripAccessService.TripAccess requireReadAccess(String tripId, String userId) {
        return tripAccessService.requirePermission(tripId, userId, TripPermission.READ);
    }

    private TripAccessService.TripAccess requireWriteAccess(String tripId, String userId) {
        return tripAccessService.requirePermission(tripId, userId, TripPermission.EDIT_CONTENT);
    }

    /** 已结束旅程内容锁定：禁止新增/修改/删除日志及精选照片 */
    private void requireTripContentMutable(Trip trip) {
        if (trip != null && TripStatus.isCompleted(DateTimeUtil.todayBusiness(), trip.getStartDate(), trip.getEndDate())) {
            throw new BizException(ErrorCode.TRIP_COMPLETED_CONTENT_LOCKED);
        }
    }

    private void requireEntryOwnership(JournalEntry entry) {
        String currentUserId = LoginUserContext.requireUserId();
        if (!currentUserId.equals(entry.getUserId())) {
            throw new ForbiddenException();
        }
    }

    private String normalizeVisibility(String visibility) {
        if (visibility == null || !StringUtils.hasText(visibility.trim())) {
            return VISIBILITY_ALL;
        }
        String trimmed = visibility.trim().toLowerCase();
        if (VISIBILITY_ALL.equals(trimmed) || VISIBILITY_PRIVATE.equals(trimmed)) {
            return trimmed;
        }
        throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "visibility 仅支持 all / private");
    }

    private String resolveAuthorName(String userId) {
        if (userMapper == null || userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user != null ? user.getNickname() : null;
    }

    private Map<String, String> resolveAuthorNames(List<JournalEntry> entries) {
        if (userMapper == null || entries.isEmpty()) {
            return Map.of();
        }
        List<String> userIds = entries.stream()
                .map(JournalEntry::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : "",
                        (left, right) -> left));
    }

    private List<File> validateFileIds(List<String> fileIds, String userId, String tripId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (fileIds.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.JOURNAL_PHOTO_LIMIT);
        }

        List<String> validIds = fileIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (validIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<File> files = fileMapper.selectBatchIds(validIds);
        if (files.size() != validIds.size()) {
            throw new BizException(ErrorCode.FILE_NOT_FOUND, "部分 fileId 对应的文件不存在");
        }

        for (File file : files) {
            if (!userId.equals(file.getUserId())) {
                throw new ForbiddenException();
            }
            if (!"UPLOADED".equals(file.getStatus())) {
                throw new BizException(ErrorCode.FILE_NOT_UPLOADED);
            }
            if (!IMAGE_CONTENT_TYPES.contains(file.getContentType())) {
                throw new BizException(ErrorCode.FILE_NOT_IMAGE);
            }
            if (!"JOURNAL_PHOTO".equals(file.getBusinessType()) || !tripId.equals(file.getBusinessId())) {
                throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileIds 必须是当前旅行的 JOURNAL_PHOTO 文件");
            }
        }

        Map<String, File> fileMap = files.stream().collect(Collectors.toMap(File::getId, f -> f));
        List<File> ordered = new ArrayList<>(validIds.size());
        for (String id : validIds) {
            ordered.add(fileMap.get(id));
        }
        return ordered;
    }

    private List<JournalPhoto> replacePhotos(JournalEntry entry, List<File> files) {
        Set<String> featuredFileIds = findPhotosByEntryId(entry.getId(), entry.getTripId()).stream()
                .filter(photo -> Boolean.TRUE.equals(photo.getIsFeatured()))
                .map(JournalPhoto::getFileId)
                .collect(Collectors.toSet());
        journalPhotoMapper.softDeleteAllByJournalEntryId(entry.getId(), entry.getTripId());
        journalPhotoMapper.softDeleteByJournalEntryId(entry.getId(), entry.getTripId(), entry.getUserId());
        if (files.isEmpty()) {
            return Collections.emptyList();
        }
        List<JournalPhoto> savedPhotos = new ArrayList<>(files.size());
        for (int i = 0; i < files.size(); i++) {
            JournalPhoto photo = new JournalPhoto();
            photo.setJournalEntryId(entry.getId());
            photo.setTripId(entry.getTripId());
            photo.setUserId(entry.getUserId());
            photo.setFileId(files.get(i).getId());
            photo.setSortOrder(i + 1);
            photo.setIsFeatured(featuredFileIds.contains(files.get(i).getId()));
            journalPhotoMapper.insert(photo);
            savedPhotos.add(photo);
        }
        return savedPhotos;
    }

    private List<JournalPhoto> findPhotosByEntryId(String entryId, String tripId) {
        return journalPhotoMapper.selectList(new QueryWrapper<JournalPhoto>()
                .eq("journal_entry_id", entryId)
                .eq("trip_id", tripId)
                .isNull("deleted_at")
                .orderByAsc("sort_order")
                .orderByAsc("created_at"));
    }

    private Map<String, List<JournalPhoto>> findPhotosByEntryIds(List<JournalEntry> entries) {
        if (entries.isEmpty()) {
            return Map.of();
        }
        List<String> entryIds = entries.stream().map(JournalEntry::getId).toList();
        List<JournalPhoto> photos = journalPhotoMapper.selectList(new QueryWrapper<JournalPhoto>()
                .in("journal_entry_id", entryIds)
                .orderByAsc("journal_entry_id")
                .orderByAsc("sort_order")
                .orderByAsc("created_at"));
        return photos.stream()
                .collect(Collectors.groupingBy(JournalPhoto::getJournalEntryId));
    }

    private Map<String, File> findFilesByPhotoEntries(Map<String, List<JournalPhoto>> photosByEntryId) {
        List<JournalPhoto> allPhotos = photosByEntryId.values().stream()
                .flatMap(List::stream)
                .toList();
        return findFilesByPhotoList(allPhotos);
    }

    private Map<String, File> findFilesByPhotoList(List<JournalPhoto> photos) {
        if (photos.isEmpty()) {
            return Map.of();
        }
        List<String> fileIds = photos.stream()
                .map(JournalPhoto::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        List<File> files = fileMapper.selectBatchIds(fileIds);
        return files.stream().collect(Collectors.toMap(File::getId, f -> f));
    }

    private JournalEntryResp toResp(JournalEntry entry, List<JournalPhoto> photos, Map<String, File> filesById,
                                    String authorName) {
        List<JournalEntryResp.PhotoResp> photoResps = photos.stream()
                .sorted(Comparator.comparing(JournalPhoto::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(photo -> toPhotoResp(photo, filesById.get(photo.getFileId())))
                .toList();

        return new JournalEntryResp(
                entry.getId(),
                entry.getTripId(),
                entry.getUserId(),
                authorName,
                entry.getContent(),
                toLocationResp(entry),
                photoResps,
                DateTimeUtil.toInstantString(entry.getOccurredAt()),
                entry.getMood(),
                entry.getIsHighlight(),
                entry.getVisibility(),
                DateTimeUtil.toInstantString(entry.getCreatedAt())
        );
    }

    private JournalEntryResp.PhotoResp updatePhotoFeatured(String tripId, String photoId, boolean featured) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        requireTripContentMutable(access.trip());
        JournalPhoto photo = journalPhotoMapper.selectOne(new QueryWrapper<JournalPhoto>()
                .eq("id", photoId)
                .eq("trip_id", tripId));
        if (photo == null) {
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND, "日志照片不存在");
        }
        // Check ownership via the parent journal entry
        JournalEntry parentEntry = journalEntryMapper.selectOne(new QueryWrapper<JournalEntry>()
                .eq("id", photo.getJournalEntryId())
                .eq("trip_id", tripId));
        if (parentEntry == null) {
            throw new NotFoundException(ErrorCode.JOURNAL_NOT_FOUND);
        }
        requireEntryOwnership(parentEntry);
        if (featured && VISIBILITY_PRIVATE.equals(parentEntry.getVisibility())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "私密日志照片不能加入旅程精选");
        }
        boolean oldValue = Boolean.TRUE.equals(photo.getIsFeatured());
        if (oldValue == featured) {
            return toPhotoResp(photo, fileMapper.selectById(photo.getFileId()));
        }
        tripMapper.lockByIdForUpdate(tripId);
        File file = fileMapper.selectById(photo.getFileId());
        if (file == null || !"UPLOADED".equals(file.getStatus())) {
            throw new BizException(ErrorCode.FILE_NOT_UPLOADED);
        }
        if (journalPhotoMapper.updateFeatured(tripId, photoId, featured) == 0) {
            JournalPhoto stillExists = journalPhotoMapper.selectOne(new QueryWrapper<JournalPhoto>()
                    .eq("id", photoId)
                    .eq("trip_id", tripId)
                    .isNull("deleted_at"));
            if (stillExists != null) {
                throw new BizException(ErrorCode.TRIP_FEATURED_PHOTO_LIMIT);
            }
            throw new NotFoundException(ErrorCode.FILE_NOT_FOUND, "日志照片不存在");
        }
        photo.setIsFeatured(featured);
        // Only record activity for non-private entries
        if (!VISIBILITY_PRIVATE.equals(parentEntry.getVisibility())) {
            recordActivity(
                    tripId,
                    userId,
                    memberId(access),
                    featured ? "JOURNAL_PHOTO_FEATURED" : "JOURNAL_PHOTO_UNFEATURED",
                    "JOURNAL_PHOTO",
                    photoId,
                    List.of(change("isFeatured", oldValue, featured)),
                    featured ? "精选旅行照片" : "取消精选旅行照片");
        }
        return toPhotoResp(photo, file);
    }

    private JournalEntryResp.PhotoResp toPhotoResp(JournalPhoto photo, File file) {
        var ciUrls = fileUrlResolver.resolveUrls(file);
        return new JournalEntryResp.PhotoResp(
                photo.getId(),
                photo.getFileId(),
                ciUrls.url(),
                photo.getSortOrder(),
                ciUrls.thumbnailUrl(),
                ciUrls.previewUrl(),
                ciUrls.exportUrl(),
                ciUrls.storyPortraitUrl(),
                ciUrls.storyCardUrl(),
                ciUrls.squareUrl(),
                ciUrls.watermarkedUrl(),
                file != null ? file.getWidth() : null,
                file != null ? file.getHeight() : null,
                Boolean.TRUE.equals(photo.getIsFeatured()));
    }

    private String normalizeOptionalMood(String mood) {
        if (mood == null) {
            return null;
        }
        String trimmed = mood.trim();
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        try {
            return JournalMood.fromValue(trimmed).value();
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "mood 仅支持 happy / relaxed / excited / peaceful / thoughtful");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_CONTENT_LENGTH) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "content 长度不能超过 " + MAX_CONTENT_LENGTH);
        }
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private String memberId(TripAccessService.TripAccess access) {
        return access == null || access.member() == null ? null : access.member().getId();
    }

    private String lengthString(String value) {
        return value == null ? "0" : String.valueOf(value.length());
    }

    private void recordActivity(String tripId, String actorUserId, String actorMemberId, String action,
                                String targetType, String targetId,
                                List<TripActivityLogResp.FieldChange> changes, String summary) {
        if (activityLogService == null) {
            return;
        }
        activityLogService.record(tripId, actorUserId, actorMemberId, action, targetType, targetId, changes, summary);
    }

    private List<TripActivityLogResp.FieldChange> changes(TripActivityLogResp.FieldChange... changes) {
        return java.util.Arrays.stream(changes)
                .filter(change -> change != null && !java.util.Objects.equals(change.oldValue(), change.newValue()))
                .toList();
    }

    private TripActivityLogResp.FieldChange change(String field, Object oldValue, Object newValue) {
        String oldString = oldValue == null ? null : String.valueOf(oldValue);
        String newString = newValue == null ? null : String.valueOf(newValue);
        return new TripActivityLogResp.FieldChange(field, oldString, newString);
    }

    private void applyLocationForCreate(JournalEntry entry, JournalEntryLocationReq location) {
        if (location == null || !hasLocationContent(location)) {
            return;
        }
        setLocationFields(entry, location);
    }

    private void applyLocationForUpdate(JournalEntry entry, JournalEntryLocationReq location) {
        if (!hasLocationContent(location)) {
            clearLocationFields(entry);
            return;
        }
        setLocationFields(entry, location);
    }

    private boolean hasLocationContent(JournalEntryLocationReq location) {
        return StringUtils.hasText(location.name())
                || StringUtils.hasText(location.address())
                || StringUtils.hasText(location.poiId())
                || StringUtils.hasText(location.providerPlaceId())
                || StringUtils.hasText(location.provider())
                || StringUtils.hasText(location.country())
                || StringUtils.hasText(location.adcode())
                || StringUtils.hasText(location.cityCode())
                || StringUtils.hasText(location.provinceName())
                || StringUtils.hasText(location.cityName())
                || StringUtils.hasText(location.districtName())
                || location.latitude() != null
                || location.longitude() != null;
    }

    private void setLocationFields(JournalEntry entry, JournalEntryLocationReq location) {
        String name = normalizeOptional(location.name());
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "location.name 不能为空");
        }
        entry.setLocationName(name);
        entry.setLocationAddress(normalizeOptional(location.address()));
        entry.setLatitude(location.latitude());
        entry.setLongitude(location.longitude());
        entry.setLocationPoiId(resolveProviderPlaceId(location));
        entry.setLocationProvider(normalizeProvider(location.provider()));
        entry.setLocationCountry(normalizeOptional(location.country()));
        entry.setLocationAdcode(normalizeOptional(location.adcode()));
        entry.setLocationCityCode(normalizeOptional(location.cityCode()));
        entry.setLocationProvinceName(normalizeOptional(location.provinceName()));
        entry.setLocationCityName(normalizeOptional(location.cityName()));
        entry.setLocationDistrictName(normalizeOptional(location.districtName()));
    }

    private String resolveProviderPlaceId(JournalEntryLocationReq location) {
        String providerPlaceId = normalizeOptional(location.providerPlaceId());
        if (StringUtils.hasText(providerPlaceId)) {
            return providerPlaceId;
        }
        return normalizeOptional(location.poiId());
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return LocationProvider.MANUAL.name();
        }
        String normalized = provider.trim().toUpperCase();
        try {
            return LocationProvider.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "location.provider 不支持");
        }
    }

    private void clearLocationFields(JournalEntry entry) {
        entry.setLocationName(null);
        entry.setLocationAddress(null);
        entry.setLatitude(null);
        entry.setLongitude(null);
        entry.setLocationPoiId(null);
        entry.setLocationProvider(null);
        entry.setLocationCountry(null);
        entry.setLocationAdcode(null);
        entry.setLocationCityCode(null);
        entry.setLocationProvinceName(null);
        entry.setLocationCityName(null);
        entry.setLocationDistrictName(null);
    }

    private JournalEntryLocationResp toLocationResp(JournalEntry entry) {
        if (!StringUtils.hasText(entry.getLocationName())) {
            return null;
        }
        return new JournalEntryLocationResp(
                entry.getLocationPoiId(),
                entry.getLocationName(),
                entry.getLocationAddress(),
                entry.getLatitude(),
                entry.getLongitude(),
                entry.getLocationAdcode(),
                entry.getLocationCityCode(),
                entry.getLocationProvinceName(),
                entry.getLocationCityName(),
                entry.getLocationDistrictName(),
                entry.getLocationProvider(),
                entry.getLocationPoiId(),
                entry.getLocationCountry()
        );
    }

}
