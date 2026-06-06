package com.oneapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RelayLog {
    private long id;
    private long timestamp;     // Unix 时间戳秒数
    @JsonProperty("channel_id")
    private int instanceId;     // 实例 ID
    private String baseUrl;
    private String tokenName;
    private int userId;
    private String modelOrig;   // 用户请求的模型
    private String upstreamModel; // 实际调用的模型（DB 列 model_real）
    private boolean stream;
    private int bodySize;
    private int httpStatus;
    private int respSize;
    private int tokens;
    private long latencyMs;
    private String errorMessage;
}
