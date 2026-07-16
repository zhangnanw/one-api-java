package com.oneapi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA 实体：relay 请求日志。
 * 对应表 relay_logs。
 */
@Entity
@Table(name = "relay_logs")
@Getter
@Setter
public class RelayLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ts", nullable = false)
    private Instant ts;

    @Column(name = "channel_id")
    private Integer instanceId;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "token_name")
    private String tokenName;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "model_orig")
    private String modelOrig;

    @Column(name = "model_real")
    private String upstreamModel;

    @Column(name = "stream")
    private Integer stream;

    @Column(name = "body_size")
    private Integer bodySize;

    @Column(name = "code")
    private Integer code;

    @Column(name = "resp_size")
    private Integer respSize;

    @Column(name = "tokens")
    private Integer tokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "err")
    private String errorMessage;
}
