package com.tracci.module.feedback.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.enums.ClientPlatform;
import com.tracci.common.exception.BizException;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.feedback.dto.request.FeedbackCreateReq;
import com.tracci.module.feedback.dto.response.FeedbackAdminDetailResp;
import com.tracci.module.feedback.dto.response.FeedbackAdminListItemResp;
import com.tracci.module.feedback.dto.response.FeedbackCreateResp;
import com.tracci.module.feedback.dto.response.FeedbackDetailResp;
import com.tracci.module.feedback.dto.response.FeedbackListItemResp;
import com.tracci.module.feedback.dto.response.FeedbackOptionsResp;
import com.tracci.module.feedback.entity.Feedback;
import com.tracci.module.feedback.entity.FeedbackAttachment;
import com.tracci.module.feedback.enums.FeedbackContactType;
import com.tracci.module.feedback.enums.FeedbackStatus;
import com.tracci.module.feedback.enums.FeedbackType;
import com.tracci.module.feedback.repository.FeedbackAttachmentMapper;
import com.tracci.module.feedback.repository.FeedbackMapper;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final int MAX_IMAGES = 3;
    private static final int CONTENT_PREVIEW_LENGTH = 100;
    private static final int MAX_SYSTEM_INFO_LENGTH = 2000;
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

    private final FeedbackMapper feedbackMapper;
    private final FeedbackAttachmentMapper feedbackAttachmentMapper;
    private final FileMapper fileMapper;
    private final UserMapper userMapper;
    private final FileUrlResolver fileUrlResolver;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public FeedbackCreateResp create(FeedbackCreateReq req) {
        String userId = LoginUserContext.requireUserId();
        FeedbackType type = FeedbackType.fromValue(req.type());
        String content = normalizeRequired(req.content(), "反馈内容不能为空");
        String title = normalizeOptional(req.title());
        FeedbackContactType contactType = FeedbackContactType.fromValue(req.contactType());
        String contact = normalizeOptional(req.contact());
        if (StringUtils.hasText(contact) && contactType == null) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "填写联系方式时必须指定 contactType");
        }
        if (contactType != null && !StringUtils.hasText(contact)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "指定 contactType 时必须填写 contact");
        }

        List<File> validatedFiles = validateFileIds(req.imageIds(), userId);
        ClientPlatform platform = ClientPlatform.from(req.platform());
        String appVersion = normalizeOptional(req.appVersion());
        String systemInfoJson = serializeSystemInfo(req.systemInfo());

        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setType(type.value());
        feedback.setTitle(title);
        feedback.setContent(content);
        feedback.setContactType(contactType != null ? contactType.value() : null);
        feedback.setContact(contact);
        feedback.setStatus(FeedbackStatus.SUBMITTED.value());
        feedback.setAppPlatform(platform.name());
        feedback.setAppVersion(appVersion);
        feedback.setSystemInfo(systemInfoJson);
        feedbackMapper.insert(feedback);

        saveAttachments(feedback, validatedFiles);
        bindFilesToFeedback(validatedFiles, feedback.getId());

        return new FeedbackCreateResp(
                feedback.getId(),
                feedback.getStatus(),
                DateTimeUtil.toInstantString(feedback.getCreatedAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeedbackListItemResp> listMine(int page, int pageSize) {
        String userId = LoginUserContext.requireUserId();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);

        QueryWrapper<Feedback> query = new QueryWrapper<Feedback>()
                .eq("user_id", userId)
                .orderByDesc("created_at");

        Page<Feedback> result = feedbackMapper.selectPage(new Page<>(safePage, safePageSize), query);
        List<FeedbackListItemResp> list = result.getRecords().stream()
                .map(this::toListItem)
                .toList();
        return PageResponse.of(list, result.getTotal(), safePage, safePageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackDetailResp getDetail(String feedbackId) {
        String userId = LoginUserContext.requireUserId();
        Feedback feedback = requireOwnedFeedback(feedbackId, userId);
        List<FeedbackAttachment> attachments = findAttachmentsByFeedbackId(feedback.getId());
        Map<String, File> filesById = findFilesByAttachments(attachments);
        return toDetailResp(feedback, attachments, filesById);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackOptionsResp getOptions() {
        List<FeedbackOptionsResp.OptionItem> types = List.of(FeedbackType.values()).stream()
                .map(type -> new FeedbackOptionsResp.OptionItem(type.value(), type.label()))
                .toList();
        List<FeedbackOptionsResp.OptionItem> statuses = List.of(FeedbackStatus.values()).stream()
                .map(status -> new FeedbackOptionsResp.OptionItem(status.value(), status.label()))
                .toList();
        List<FeedbackOptionsResp.OptionItem> contactTypes = List.of(FeedbackContactType.values()).stream()
                .map(type -> new FeedbackOptionsResp.OptionItem(type.value(), type.label()))
                .toList();
        List<FeedbackOptionsResp.OptionItem> platforms = List.of(
                ClientPlatform.WECHAT_MINI,
                ClientPlatform.IOS,
                ClientPlatform.H5,
                ClientPlatform.UNKNOWN).stream()
                .map(platform -> new FeedbackOptionsResp.OptionItem(platform.name(), platformLabel(platform)))
                .toList();
        return new FeedbackOptionsResp(types, statuses, contactTypes, platforms);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeedbackAdminListItemResp> listAll(
            String type,
            String status,
            String platform,
            String userId,
            String keyword,
            String createdFrom,
            String createdTo,
            int page,
            int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);

        QueryWrapper<Feedback> query = new QueryWrapper<Feedback>().orderByDesc("created_at");
        if (StringUtils.hasText(type)) {
            query.eq("type", type.trim().toUpperCase());
        }
        if (StringUtils.hasText(status)) {
            query.eq("status", status.trim().toUpperCase());
        }
        if (StringUtils.hasText(platform)) {
            query.eq("app_platform", platform.trim().toUpperCase());
        }
        if (StringUtils.hasText(userId)) {
            query.eq("user_id", userId.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            query.and(w -> w.like("title", kw).or().like("content", kw));
        }
        LocalDateTime from = parseInstantParam(createdFrom, "createdFrom");
        LocalDateTime to = parseInstantParam(createdTo, "createdTo");
        if (from != null) {
            query.ge("created_at", from);
        }
        if (to != null) {
            query.le("created_at", to);
        }

        Page<Feedback> result = feedbackMapper.selectPage(new Page<>(safePage, safePageSize), query);
        List<Feedback> records = result.getRecords();
        Map<String, String> nicknameByUserId = loadNicknames(
                records.stream().map(Feedback::getUserId).filter(Objects::nonNull).distinct().toList());
        Map<String, Integer> attachmentCountByFeedbackId = countAttachments(
                records.stream().map(Feedback::getId).toList());

        List<FeedbackAdminListItemResp> list = records.stream()
                .map(feedback -> toAdminListItem(
                        feedback,
                        nicknameByUserId.get(feedback.getUserId()),
                        attachmentCountByFeedbackId.getOrDefault(feedback.getId(), 0)))
                .toList();
        return PageResponse.of(list, result.getTotal(), safePage, safePageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public FeedbackAdminDetailResp getAdminDetail(String feedbackId) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new NotFoundException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        List<FeedbackAttachment> attachments = findAttachmentsByFeedbackId(feedback.getId());
        Map<String, File> filesById = findFilesByAttachments(attachments);
        User user = StringUtils.hasText(feedback.getUserId())
                ? userMapper.selectById(feedback.getUserId())
                : null;
        return toAdminDetailResp(feedback, user, attachments, filesById);
    }

    private Feedback requireOwnedFeedback(String feedbackId, String userId) {
        Feedback feedback = feedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new NotFoundException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        if (!userId.equals(feedback.getUserId())) {
            throw new NotFoundException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        return feedback;
    }

    private List<File> validateFileIds(List<String> fileIds, String userId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (fileIds.size() > MAX_IMAGES) {
            throw new BizException(ErrorCode.FEEDBACK_ATTACHMENT_LIMIT);
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
            if (!"FEEDBACK_ATTACHMENT".equals(file.getBusinessType())) {
                throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "fileIds 必须是 FEEDBACK_ATTACHMENT 类型文件");
            }
        }

        Map<String, File> fileMap = files.stream().collect(Collectors.toMap(File::getId, f -> f));
        List<File> ordered = new ArrayList<>(validIds.size());
        for (String id : validIds) {
            ordered.add(fileMap.get(id));
        }
        return ordered;
    }

    private void saveAttachments(Feedback feedback, List<File> files) {
        if (files.isEmpty()) {
            return;
        }
        for (int i = 0; i < files.size(); i++) {
            FeedbackAttachment attachment = new FeedbackAttachment();
            attachment.setFeedbackId(feedback.getId());
            attachment.setUserId(feedback.getUserId());
            attachment.setFileId(files.get(i).getId());
            attachment.setSortOrder(i);
            feedbackAttachmentMapper.insert(attachment);
        }
    }

    private void bindFilesToFeedback(List<File> files, String feedbackId) {
        for (File file : files) {
            file.setBusinessId(feedbackId);
            file.setUpdatedAt(DateTimeUtil.nowUtc());
            fileMapper.updateById(file);
        }
    }

    private List<FeedbackAttachment> findAttachmentsByFeedbackId(String feedbackId) {
        return feedbackAttachmentMapper.selectList(new QueryWrapper<FeedbackAttachment>()
                .eq("feedback_id", feedbackId)
                .orderByAsc("sort_order"));
    }

    private Map<String, File> findFilesByAttachments(List<FeedbackAttachment> attachments) {
        if (attachments.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> fileIds = attachments.stream().map(FeedbackAttachment::getFileId).toList();
        return fileMapper.selectBatchIds(fileIds).stream()
                .collect(Collectors.toMap(File::getId, f -> f));
    }

    private FeedbackListItemResp toListItem(Feedback feedback) {
        return new FeedbackListItemResp(
                feedback.getId(),
                feedback.getType(),
                feedback.getTitle(),
                buildContentPreview(feedback.getContent()),
                feedback.getStatus(),
                DateTimeUtil.toInstantString(feedback.getCreatedAt()));
    }

    private FeedbackAdminListItemResp toAdminListItem(
            Feedback feedback,
            String userNickname,
            int attachmentCount) {
        return new FeedbackAdminListItemResp(
                feedback.getId(),
                feedback.getUserId(),
                userNickname,
                feedback.getType(),
                feedback.getTitle(),
                buildContentPreview(feedback.getContent()),
                feedback.getStatus(),
                feedback.getAppPlatform(),
                feedback.getAppVersion(),
                attachmentCount,
                DateTimeUtil.toInstantString(feedback.getCreatedAt()),
                DateTimeUtil.toInstantString(feedback.getHandledAt()));
    }

    private FeedbackAdminDetailResp toAdminDetailResp(
            Feedback feedback,
            User user,
            List<FeedbackAttachment> attachments,
            Map<String, File> filesById) {
        List<FeedbackDetailResp.AttachmentResp> attachmentResps = attachments.stream()
                .map(attachment -> toAttachmentResp(attachment, filesById.get(attachment.getFileId())))
                .filter(Objects::nonNull)
                .toList();
        FeedbackAdminDetailResp.UserSummary userSummary = user == null
                ? new FeedbackAdminDetailResp.UserSummary(
                        feedback.getUserId(), null, null, null, null)
                : new FeedbackAdminDetailResp.UserSummary(
                        user.getId(),
                        user.getNickname(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getLoginType());
        return new FeedbackAdminDetailResp(
                feedback.getId(),
                feedback.getUserId(),
                userSummary,
                feedback.getType(),
                feedback.getTitle(),
                feedback.getContent(),
                feedback.getContactType(),
                feedback.getContact(),
                feedback.getStatus(),
                feedback.getAppPlatform(),
                feedback.getAppVersion(),
                parseSystemInfo(feedback.getSystemInfo()),
                attachmentResps.isEmpty() ? null : attachmentResps,
                feedback.getHandlerNote(),
                DateTimeUtil.toInstantString(feedback.getHandledAt()),
                DateTimeUtil.toInstantString(feedback.getCreatedAt()),
                DateTimeUtil.toInstantString(feedback.getUpdatedAt()));
    }

    private FeedbackDetailResp toDetailResp(
            Feedback feedback,
            List<FeedbackAttachment> attachments,
            Map<String, File> filesById) {
        List<FeedbackDetailResp.AttachmentResp> attachmentResps = attachments.stream()
                .map(attachment -> toAttachmentResp(attachment, filesById.get(attachment.getFileId())))
                .filter(resp -> resp != null)
                .toList();
        return new FeedbackDetailResp(
                feedback.getId(),
                feedback.getType(),
                feedback.getTitle(),
                feedback.getContent(),
                feedback.getContactType(),
                feedback.getContact(),
                feedback.getStatus(),
                feedback.getAppPlatform(),
                feedback.getAppVersion(),
                parseSystemInfo(feedback.getSystemInfo()),
                attachmentResps.isEmpty() ? null : attachmentResps,
                DateTimeUtil.toInstantString(feedback.getCreatedAt()));
    }

    private Map<String, String> loadNicknames(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> nicknames = new HashMap<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            if (user != null && user.getId() != null) {
                nicknames.put(user.getId(), user.getNickname());
            }
        }
        return nicknames;
    }

    private Map<String, Integer> countAttachments(List<String> feedbackIds) {
        if (feedbackIds == null || feedbackIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<FeedbackAttachment> attachments = feedbackAttachmentMapper.selectList(
                new QueryWrapper<FeedbackAttachment>().in("feedback_id", feedbackIds));
        Map<String, Integer> counts = new HashMap<>();
        for (FeedbackAttachment attachment : attachments) {
            counts.merge(attachment.getFeedbackId(), 1, Integer::sum);
        }
        return counts;
    }

    private LocalDateTime parseInstantParam(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(value.trim()), ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, fieldName + " 必须是 ISO-8601 时间");
        }
    }

    private FeedbackDetailResp.AttachmentResp toAttachmentResp(FeedbackAttachment attachment, File file) {
        if (file == null || !"UPLOADED".equals(file.getStatus())) {
            return null;
        }
        CiUrlBuilder.CiUrls urls = fileUrlResolver.resolveUrls(file);
        return new FeedbackDetailResp.AttachmentResp(
                attachment.getId(),
                file.getId(),
                urls.url(),
                attachment.getSortOrder(),
                urls.thumbnailUrl(),
                urls.previewUrl(),
                urls.exportUrl(),
                file.getWidth(),
                file.getHeight());
    }

    private String buildContentPreview(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= CONTENT_PREVIEW_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, CONTENT_PREVIEW_LENGTH) + "...";
    }

    private String serializeSystemInfo(Map<String, Object> systemInfo) {
        if (systemInfo == null || systemInfo.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(systemInfo);
            if (json.length() > MAX_SYSTEM_INFO_LENGTH) {
                throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "systemInfo 长度不能超过 " + MAX_SYSTEM_INFO_LENGTH);
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "systemInfo 格式不合法");
        }
    }

    private Object parseSystemInfo(String systemInfoJson) {
        if (!StringUtils.hasText(systemInfoJson)) {
            return null;
        }
        try {
            return objectMapper.readValue(systemInfoJson, Object.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private String normalizeRequired(String value, String message) {
        String trimmed = normalizeOptional(value);
        if (!StringUtils.hasText(trimmed)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, message);
        }
        return trimmed;
    }

    private static String platformLabel(ClientPlatform platform) {
        return switch (platform) {
            case WECHAT_MINI -> "微信小程序";
            case IOS -> "iOS";
            case H5 -> "H5";
            case UNKNOWN -> "未知";
        };
    }
}
