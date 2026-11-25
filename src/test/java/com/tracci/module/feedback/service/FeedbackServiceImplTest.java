package com.tracci.module.feedback.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.AuthException;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.common.response.PageResponse;
import com.tracci.module.feedback.dto.request.FeedbackCreateReq;
import com.tracci.module.feedback.dto.response.FeedbackAdminDetailResp;
import com.tracci.module.feedback.dto.response.FeedbackAdminListItemResp;
import com.tracci.module.feedback.dto.response.FeedbackCreateResp;
import com.tracci.module.feedback.dto.response.FeedbackDetailResp;
import com.tracci.module.feedback.dto.response.FeedbackListItemResp;
import com.tracci.module.feedback.entity.Feedback;
import com.tracci.module.feedback.entity.FeedbackAttachment;
import com.tracci.module.feedback.repository.FeedbackAttachmentMapper;
import com.tracci.module.feedback.repository.FeedbackMapper;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedbackServiceImplTest {

    @Mock
    private FeedbackMapper feedbackMapper;
    @Mock
    private FeedbackAttachmentMapper feedbackAttachmentMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private FileUrlResolver fileUrlResolver;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private FeedbackServiceImpl feedbackService;

    @BeforeEach
    void setUp() throws Exception {
        LoginUserContext.setUserId("user-1");
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{\"brand\":\"iPhone\"}");
        lenient().when(objectMapper.readValue(any(String.class), eq(Object.class))).thenReturn(Map.of("brand", "iPhone"));
        lenient().when(fileUrlResolver.resolveUrls(any(File.class))).thenAnswer(inv -> {
            File file = inv.getArgument(0);
            String url = file != null ? file.getUrl() : null;
            return CiUrlBuilder.CiUrls.mirror(url);
        });
    }

    @AfterEach
    void tearDown() {
        LoginUserContext.clear();
    }

    @Test
    void create_savesFeedbackWithAttachments() {
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "user-1")));
        doAnswer(invocation -> {
            Feedback feedback = invocation.getArgument(0);
            feedback.setId("fb-1");
            feedback.setCreatedAt(LocalDateTime.of(2026, 5, 31, 8, 0));
            return 1;
        }).when(feedbackMapper).insert(any(Feedback.class));

        FeedbackCreateReq req = new FeedbackCreateReq(
                "BUG",
                "导出失败",
                "点击保存后提示失败",
                "WECHAT",
                "wx-id",
                List.of("file-1"),
                "WECHAT_MINI",
                "1.0.0",
                Map.of("brand", "iPhone"));

        FeedbackCreateResp resp = feedbackService.create(req);

        assertEquals("fb-1", resp.id());
        assertEquals("SUBMITTED", resp.status());
        assertNotNull(resp.createdAt());

        ArgumentCaptor<Feedback> feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackMapper).insert(feedbackCaptor.capture());
        Feedback saved = feedbackCaptor.getValue();
        assertEquals("user-1", saved.getUserId());
        assertEquals("BUG", saved.getType());
        assertEquals("SUBMITTED", saved.getStatus());
        assertEquals("WECHAT_MINI", saved.getAppPlatform());

        verify(feedbackAttachmentMapper).insert(any(FeedbackAttachment.class));
        verify(fileMapper).updateById(any(File.class));
    }

    @Test
    void create_withoutLogin_throwsAuthException() {
        LoginUserContext.clear();
        assertThrows(AuthException.class, () -> feedbackService.create(validCreateReq()));
    }

    @Test
    void create_rejectsBlankContent() {
        FeedbackCreateReq req = new FeedbackCreateReq(
                "BUG", null, "   ", null, null, null, null, null, null);
        BizException ex = assertThrows(BizException.class, () -> feedbackService.create(req));
        assertEquals(ErrorCode.AUTH_PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void create_rejectsInvalidType() {
        FeedbackCreateReq req = new FeedbackCreateReq(
                "INVALID", null, "反馈内容", null, null, null, null, null, null);
        BizException ex = assertThrows(BizException.class, () -> feedbackService.create(req));
        assertEquals(ErrorCode.AUTH_PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void create_rejectsTooManyImageIds() {
        FeedbackCreateReq req = new FeedbackCreateReq(
                "BUG", null, "反馈内容", null, null,
                List.of("file-1", "file-2", "file-3", "file-4"),
                null, null, null);

        BizException ex = assertThrows(BizException.class, () -> feedbackService.create(req));
        assertEquals(ErrorCode.FEEDBACK_ATTACHMENT_LIMIT, ex.getErrorCode());
    }

    @Test
    void create_rejectsFileOwnedByOtherUser() {
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "other-user")));

        FeedbackCreateReq req = validCreateReq();
        assertThrows(ForbiddenException.class, () -> feedbackService.create(req));
    }

    @Test
    void create_acceptsNullSystemInfo() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenCallRealMethod();
        doAnswer(invocation -> {
            Feedback feedback = invocation.getArgument(0);
            feedback.setId("fb-2");
            feedback.setCreatedAt(LocalDateTime.of(2026, 5, 31, 8, 0));
            return 1;
        }).when(feedbackMapper).insert(any(Feedback.class));

        FeedbackCreateReq req = new FeedbackCreateReq(
                "FEATURE", null, "希望增加导出 PDF", null, null, null, "WECHAT_MINI", "1.0.0", null);

        FeedbackCreateResp resp = feedbackService.create(req);
        assertEquals("fb-2", resp.id());
        verify(feedbackAttachmentMapper, never()).insert(any(FeedbackAttachment.class));
    }

    @Test
    void listMine_returnsOnlyCurrentUserFeedbacks() {
        Feedback feedback = feedback("fb-1", "user-1", "BUG", "标题", "完整内容", "SUBMITTED");
        when(feedbackMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Feedback> page = invocation.getArgument(0);
                    page.setRecords(List.of(feedback));
                    page.setTotal(1);
                    return page;
                });

        List<FeedbackListItemResp> list = feedbackService.listMine(1, 20).list();

        assertEquals(1, list.size());
        assertEquals("fb-1", list.get(0).id());
        assertEquals("完整内容", list.get(0).contentPreview());

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(feedbackMapper).selectPage(any(Page.class), queryCaptor.capture());
    }

    @Test
    void getDetail_rejectsOtherUsersFeedback() {
        when(feedbackMapper.selectById("fb-1")).thenReturn(feedback("fb-1", "other-user", "BUG", null, "内容", "SUBMITTED"));

        assertThrows(NotFoundException.class, () -> feedbackService.getDetail("fb-1"));
    }

    @Test
    void getDetail_returnsOwnedFeedbackWithoutHandlerFields() {
        Feedback feedback = feedback("fb-1", "user-1", "BUG", "标题", "完整内容", "SUBMITTED");
        feedback.setContactType("EMAIL");
        feedback.setContact("user@example.com");
        feedback.setAppPlatform("WECHAT_MINI");
        feedback.setSystemInfo("{\"brand\":\"iPhone\"}");

        FeedbackAttachment attachment = new FeedbackAttachment();
        attachment.setId("att-1");
        attachment.setFeedbackId("fb-1");
        attachment.setFileId("file-1");
        attachment.setSortOrder(0);

        when(feedbackMapper.selectById("fb-1")).thenReturn(feedback);
        when(feedbackAttachmentMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(attachment));
        when(fileMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                uploadedFile("file-1", "user-1")));

        FeedbackDetailResp resp = feedbackService.getDetail("fb-1");

        assertEquals("fb-1", resp.id());
        assertEquals("完整内容", resp.content());
        assertEquals("user@example.com", resp.contact());
        assertEquals(1, resp.attachments().size());
    }

    @Test
    void listAll_returnsAdminItemsWithoutContact() {
        Feedback feedback = feedback("fb-1", "user-2", "BUG", "导出失败", "完整内容描述", "SUBMITTED");
        feedback.setAppPlatform("IOS");
        feedback.setAppVersion("1.2.0");

        when(feedbackMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<Feedback> page = invocation.getArgument(0);
                    page.setRecords(List.of(feedback));
                    page.setTotal(1);
                    return page;
                });

        User user = new User();
        user.setId("user-2");
        user.setNickname("旅人甲");
        user.setEmail("secret@example.com");
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(user));

        FeedbackAttachment attachment = new FeedbackAttachment();
        attachment.setFeedbackId("fb-1");
        attachment.setFileId("file-1");
        when(feedbackAttachmentMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(attachment));

        PageResponse<FeedbackAdminListItemResp> page = feedbackService.listAll(
                null, null, null, null, null, null, null, 1, 20);

        assertEquals(1, page.list().size());
        FeedbackAdminListItemResp item = page.list().get(0);
        assertEquals("fb-1", item.id());
        assertEquals("user-2", item.userId());
        assertEquals("旅人甲", item.userNickname());
        assertEquals(1, item.attachmentCount());
        assertEquals("IOS", item.platform());
    }

    @Test
    void getAdminDetail_returnsOtherUsersFeedbackWithContactAndHandler() {
        Feedback feedback = feedback("fb-1", "other-user", "FEATURE", "建议", "希望支持深色模式", "SUBMITTED");
        feedback.setContactType("EMAIL");
        feedback.setContact("other@example.com");
        feedback.setHandlerNote("线下已联系");
        feedback.setHandledAt(LocalDateTime.of(2026, 6, 1, 10, 0));
        feedback.setAppPlatform("WECHAT_MINI");
        feedback.setUpdatedAt(LocalDateTime.of(2026, 6, 1, 10, 0));

        User user = new User();
        user.setId("other-user");
        user.setNickname("其他用户");
        user.setEmail("other@example.com");
        user.setLoginType("WECHAT_MINI");

        when(feedbackMapper.selectById("fb-1")).thenReturn(feedback);
        when(userMapper.selectById("other-user")).thenReturn(user);
        when(feedbackAttachmentMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        FeedbackAdminDetailResp resp = feedbackService.getAdminDetail("fb-1");

        assertEquals("fb-1", resp.id());
        assertEquals("other@example.com", resp.contact());
        assertEquals("线下已联系", resp.handlerNote());
        assertEquals("其他用户", resp.user().nickname());
        assertEquals("other@example.com", resp.user().email());
    }

    @Test
    void getAdminDetail_throwsWhenMissing() {
        when(feedbackMapper.selectById("missing")).thenReturn(null);
        assertThrows(NotFoundException.class, () -> feedbackService.getAdminDetail("missing"));
    }

    private static FeedbackCreateReq validCreateReq() {
        return new FeedbackCreateReq(
                "BUG",
                null,
                "反馈内容",
                null,
                null,
                List.of("file-1"),
                "WECHAT_MINI",
                "1.0.0",
                Map.of("brand", "iPhone"));
    }

    private static File uploadedFile(String id, String userId) {
        File file = new File();
        file.setId(id);
        file.setUserId(userId);
        file.setBusinessType("FEEDBACK_ATTACHMENT");
        file.setStatus("UPLOADED");
        file.setContentType("image/jpeg");
        file.setObjectKey("users/" + userId + "/feedback_attachment/test.jpg");
        file.setUrl("https://cdn.example.com/" + id + ".jpg");
        return file;
    }

    private static Feedback feedback(
            String id,
            String userId,
            String type,
            String title,
            String content,
            String status) {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setUserId(userId);
        feedback.setType(type);
        feedback.setTitle(title);
        feedback.setContent(content);
        feedback.setStatus(status);
        feedback.setCreatedAt(LocalDateTime.of(2026, 5, 31, 8, 0));
        return feedback;
    }
}
