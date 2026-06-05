package com.oneapi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RelayLog {
    public long id;
    public long ts;          // Unix 时间戳秒数
    @JsonProperty("channel_id")
    public int instanceId;   // 实例 ID
    public String baseUrl;
    public String tokenName;
    public int userId;
    public String modelOrig; // 用户请求的模型
    public String modelReal; // 实际调用的模型
    public boolean stream;
    public int bodySize;
    public int code;
    public int respSize;
    public int tokens;
    public long latencyMs;
    public String err;
}
