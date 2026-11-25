package com.tracci.common.response;

import lombok.Getter;

/**
 * 业务错误码枚举
 * <p>
 * 业务码段位规范：
 * 0       → 成功
 * 400xx   → 请求参数 / 验证码相关
 * 401xx   → 认证相关（Token）
 * 403xx   → 权限相关
 * 404xx   → 资源不存在
 * 422xx   → 业务层参数校验
 * 429xx   → 限流
 * 500xx   → 服务器内部错误
 * 502xx   → 第三方服务
 */
@Getter
public enum ErrorCode {

    // ---- 成功 ----
    SUCCESS(200, 0, "ok"),

    // ---- 认证相关（400xx / 401xx）----
    /** 验证码错误或已过期（邮箱/短信通用） */
    VERIFICATION_CODE_INVALID(400, 40001, "验证码错误或已过期"),
    /** 请求参数格式错误 */
    AUTH_PARAM_INVALID(400, 40002, "请求参数格式错误"),
    /** 邮箱不存在或密码错误（合并错误，防止枚举邮箱） */
    EMAIL_OR_PASSWORD_INVALID(400, 40003, "邮箱或密码错误"),
    /** 邮箱已被注册 */
    EMAIL_ALREADY_REGISTERED(400, 40004, "邮箱已被注册"),
    /** 发送频率超限（邮箱/短信通用） */
    SEND_CODE_TOO_FREQUENT(429, 42901, "发送过于频繁，请稍后再试"),
    TOKEN_INVALID(401, 40101, "登录已过期，请重新登录"),
    TOKEN_MISSING(401, 40102, "未登录"),
    /** 未设置登录密码 */
    PASSWORD_NOT_SET(400, 40009, "未设置登录密码，无法修改"),
    /** 当前登录方式暂未开放 */
    AUTH_PROVIDER_DISABLED(422, 42212, "当前登录方式暂未开放"),
    /** 手机号已被其他账号绑定 */
    PHONE_ALREADY_BOUND(400, 40010, "该手机号已被其他账号绑定"),
    /** 手机号未绑定 */
    PHONE_NOT_BOUND(400, 40011, "当前账号未绑定手机号"),

    // ---- 权限相关（403xx）----
    FORBIDDEN(403, 40301, "无权限访问该资源"),
    /** 用户已主动注销 */
    ACCOUNT_DELETED(403, 40302, "账号已注销"),

    // ---- 资源不存在（404xx）----
    TRIP_NOT_FOUND(404, 40401, "旅行不存在"),
    EXPENSE_NOT_FOUND(404, 40402, "账单不存在"),
    JOURNAL_NOT_FOUND(404, 40403, "日志不存在"),
    USER_NOT_FOUND(404, 40404, "用户不存在"),
    ITINERARY_NOT_FOUND(404, 40405, "行程节点不存在"),
    FILE_NOT_FOUND(404, 40406, "文件不存在"),
    FEEDBACK_NOT_FOUND(404, 40407, "反馈不存在"),
    TRIP_MEMBER_NOT_FOUND(404, 40418, "旅行成员不存在"),
    TRIP_INVITATION_NOT_FOUND(404, 40419, "邀请不存在或已失效"),

    // ---- 参数校验（422xx）----
    PARAM_INVALID(422, 42201, "请求参数不合法"),
    JOURNAL_PHOTO_LIMIT(422, 42202, "每条日志最多上传 9 张照片"),
    FILE_NOT_UPLOADED(422, 42203, "文件尚未上传完成"),
    FILE_NOT_IMAGE(422, 42204, "文件类型不是图片"),
    FILE_REFERENCED(422, 42205, "文件已被引用，无法删除"),
    FEEDBACK_ATTACHMENT_LIMIT(422, 42208, "反馈截图最多 3 张"),
    CONTENT_SECURITY_REJECTED(422, 42225, "你发布的内容含违规信息"),
    TRIP_MEMBER_ROLE_INVALID(422, 42226, "成员角色不合法"),
    TRIP_INVITATION_EXPIRED(422, 42227, "邀请已过期"),
    TRIP_INVITATION_USED_UP(422, 42228, "邀请使用次数已用完"),
    EXPENSE_SPLIT_INVALID(422, 42229, "分摊信息不合法"),
    EXPENSE_ITINERARY_LINK_INVALID(422, 42249, "开支关联的行程不合法"),
    EXPENSE_CATEGORY_DUPLICATE(422, 42250, "该分类名称已存在"),
    EXPENSE_CATEGORY_LIMIT(422, 42251, "自定义分类最多 20 个"),
    EXPENSE_CATEGORY_NOT_FOUND(404, 40427, "自定义分类不存在"),
    TRIP_FEATURED_PHOTO_LIMIT(422, 42232, "每趟旅行最多精选 9 张照片"),
    // ---- 旅程状态 / 内容锁定（42235–42237）----
    /** 修改日期会改变旅程状态（如已结束→进行中），需要用户确认后重试 */
    TRIP_STATUS_CHANGE_REQUIRES_CONFIRM(422, 42235, "修改日期会改变旅程状态，需要确认"),
    /** 已结束的旅程节点仅允许修改备注（备注不影响结构与状态） */
    ITINERARY_COMPLETED_NODE_NOTE_ONLY(422, 42236, "已结束的旅程节点仅允许修改备注"),
    /** 已结束的旅程内容已锁定，不允许新增/编辑/删除行程·日志·开支 */
    TRIP_COMPLETED_CONTENT_LOCKED(422, 42237, "已结束的旅程内容已锁定"),
    /** 住宿中间夜投影节点不可单独删除 */
    ITINERARY_STAY_PROJECTION_DELETE_FORBIDDEN(422, 42238,
            "中间夜住宿不可单独删除，请编辑离店日期或从入住日删除整段住宿"),
    /** 住宿入住/离店日期不合法或不在旅程范围内 */
    ITINERARY_STAY_DATE_INVALID(422, 42239, "住宿日期不合法"),
    /** 账号仍是共同旅程的所有者，注销会使协作旅程失去有效 owner */
    ACCOUNT_OWNS_SHARED_TRIPS(422, 42240, "请先转让共同旅程的所有权，再注销账号"),
    /** 旅程 owner 数量不合法（过多或过少），无法执行邀请加入 */
    TRIP_OWNER_INVARIANT_VIOLATION(422, 42241, "该旅程状态异常，请联系客服处理"),

    // ---- 服务器内部错误（500xx）----
    INTERNAL_ERROR(500, 50001, "服务器内部错误"),
    WECHAT_CONFIG_MISSING(500, 50002, "微信登录服务配置缺失，请联系管理员"),

    // ---- 第三方服务（502xx）----
    WECHAT_LOGIN_FAILED(502, 50201, "微信登录失败，请重试"),
    SMS_SEND_FAILED(502, 50202, "短信发送失败，请重试"),
    EMAIL_SEND_FAILED(502, 50209, "邮件发送失败，请重试"),
    WECHAT_SERVICE_UNAVAILABLE(502, 50206, "微信服务暂不可用，请稍后再试"),
    CONTENT_SECURITY_UNAVAILABLE(502, 50212, "内容安全服务暂不可用，请稍后再试"),
    OSS_PRESIGN_FAILED(502, 50203, "文件上传预签名失败，请重试"),
    EXCHANGE_RATE_UNAVAILABLE(502, 50204, "汇率服务暂不可用"),
    LOCATION_SEARCH_UNAVAILABLE(502, 50205, "暂时无法搜索地点，请稍后再试"),
    WEATHER_UNAVAILABLE(502, 50207, "暂时无法获取天气信息，请稍后再试"),
    LOCATION_PROVIDER_DISABLED(422, 42206, "地点搜索服务当前不可用"),
    WEATHER_PROVIDER_DISABLED(422, 42207, "天气服务当前不可用"),




    // ---- 目的地资产 Builder（404xx / 422xx / 502xx）----
    ASSET_BUILD_JOB_NOT_FOUND(404, 40421, "构建任务不存在"),
    ASSET_BUILD_JOB_STATUS_INVALID(422, 42233, "当前构建任务状态不允许该操作"),
    ASSET_BUILD_INVALID_CONFIG(422, 42234, "构建任务配置不合法"),
    ASSET_BUILD_FETCH_FAILED(502, 50214, "POI 抓取失败，请稍后重试"),
    ASSET_BUILD_PUBLISH_FAILED(502, 50215, "发布失败，请稍后重试"),

    // ---- 想去的地点 / 收藏（404xx / 422xx / 409xx）----
    /** 地点不存在或已废弃 */
    PLACE_NOT_FOUND(404, 40422, "地点不存在"),
    /** 收藏地点不存在或不属于当前用户 */
    SAVED_PLACE_NOT_FOUND(404, 40423, "收藏地点不存在"),
    /** 收藏层级不支持当前使用场景 */
    SAVED_PLACE_SCOPE_MISMATCH(422, 42242, "该地点类型不支持此操作"),
    /** 收藏与当前旅程目的地不匹配 */
    SAVED_PLACE_DESTINATION_MISMATCH(422, 42243, "该地点与当前旅程目的地不匹配"),
    /** placeScope 与 category 组合不合法 */
    PLACE_INVALID_SCOPE_CATEGORY(422, 42244, "地点类型与分类组合不合法"),
    /** Place 缺少有效名称或身份不可用 */
    PLACE_IDENTITY_UNAVAILABLE(422, 42245, "地点信息不完整，无法使用"),
    /** 同一地点已收藏 */
    SAVED_PLACE_ALREADY_EXISTS(409, 40901, "该地点已收藏"),

    // ---- 地点内容 PlaceContent（404xx / 422xx / 409xx）----
    /** 地点内容版本不存在 */
    PLACE_CONTENT_NOT_FOUND(404, 40426, "地点内容版本不存在"),
    /** 内容版本当前状态不允许此操作 */
    PLACE_CONTENT_INVALID_STATE(422, 42246, "内容版本当前状态不允许此操作"),
    /** 发布前必须关联事实来源 */
    PLACE_CONTENT_SOURCE_REQUIRED(422, 42247, "发布前必须关联事实来源"),
    /** 该地点已存在已发布版本 */
    PLACE_CONTENT_ALREADY_PUBLISHED(409, 40902, "该地点已存在已发布版本"),
    /** 媒体授权无效或已过期 */
    PLACE_MEDIA_RIGHTS_INVALID(422, 42248, "媒体授权无效或已过期"),
    /** 地点身份冲突或已被合并 */
    PLACE_IDENTITY_CONFLICT(409, 40903, "地点身份冲突或已被合并");

    /** HTTP 状态码 */
    private final int httpStatus;

    /** 业务码（前端判断逻辑依据） */
    private final int code;

    /** 默认消息 */
    private final String message;

    ErrorCode(int httpStatus, int code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
