package com.oneapi.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * JPA 实体：全息调试日志。
 * 对应表 holographic_logs。
 */
@Entity
@Table(name = "holographic_logs")
@Getter
@Setter
public class HolographicLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "timestamp_ms", nullable = false)
    private Long timestampMs;

    @Column(name = "requested_model")
    private String requestedModel;

    @Column(name = "final_status")
    private String finalStatus;

    @Column(name = "final_http_code")
    private Integer finalHttpCode;

    @Column(name = "total_latency_ms")
    private Long totalLatencyMs;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "data", columnDefinition = "jsonb")
    private String data;
}
