package com.tracci.integration.cos;

import java.util.Map;

public interface CosClient {

    /**
     * 生成预签名上传 URL。
     *
     * @param objectKey   COS 对象路径
     * @param contentType 文件 MIME 类型
     * @param expireSeconds 签名有效期（秒）
     * @return 预签名 PUT URL
     */
    String generatePresignedUploadUrl(String objectKey, String contentType, int expireSeconds);

    /**
     * 生成 PostObject 表单上传凭证。
     *
     * @param objectKey     COS 对象路径
     * @param contentType   文件 MIME 类型
     * @param maxSizeBytes  最大文件大小
     * @param expireSeconds 签名有效期（秒）
     * @return POST 表单上传凭证
     */
    CosPostUploadCredential generatePostUploadCredential(
            String objectKey,
            String contentType,
            long maxSizeBytes,
            int expireSeconds);

    /**
     * 生成预签名访问 URL。
     *
     * @param objectKey     COS 对象路径
     * @param expireSeconds 签名有效期（秒）
     * @return 预签名 GET URL
     */
    String generatePresignedAccessUrl(String objectKey, int expireSeconds);

    /**
     * 生成带 COS CI 处理参数的预签名访问 URL。
     *
     * @param objectKey     COS 对象路径
     * @param expireSeconds 签名有效期（秒）
     * @param ciRule        CI 图片处理规则
     * @return 预签名 GET URL
     */
    String generatePresignedAccessUrl(String objectKey, int expireSeconds, String ciRule);

    /**
     * 服务端直传对象（如系统封面镜像）。
     */
    void putObject(String objectKey, byte[] content, String contentType);

    /**
     * 读取对象内容。用于服务端内部处理，避免私有桶预签名 URL 被外部访问策略拦截。
     */
    byte[] getObjectBytes(String objectKey);

    /**
     * 对象是否已存在。确认上传前用来避免造出无文件的媒体记录。
     */
    boolean objectExists(String objectKey);

    /**
     * 生成上传所需的 HTTP 头。
     */
    Map<String, String> generateUploadHeaders(String contentType);
}
