package com.tracci.module.auth.dto.response;

public record SmsSendResp(int expireSeconds, int retryAfterSeconds) {
}
