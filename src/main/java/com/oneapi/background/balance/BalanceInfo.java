package com.oneapi.background.balance;

/**
 * 供应商余额信息。
 *
 * @param vendorId    供应商 ID
 * @param vendorName  供应商名称
 * @param available   是否可用（余额是否充足）
 * @param totalBalance 总余额（原始字符串，各家格式不同）
 * @param currency    货币单位（如 USD、CNY，各家可能不同）
 * @param rawResponse 原始响应（调试用）
 */
public record BalanceInfo(
    int vendorId,
    String vendorName,
    boolean available,
    String totalBalance,
    String currency,
    String rawResponse
) {}
