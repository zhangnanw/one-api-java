package com.oneapi.background.balance;

import com.oneapi.entity.Vendor;

/**
 * 供应商余额查询接口。
 * 每个供应商一个实现类，自行判断是否负责该供应商（supports）。
 * 收到完整 Vendor 对象，优先用 balanceCredential，为空则 fallback 到 apiKey。
 */
public interface BalanceProvider {

    /**
     * 判断是否负责该供应商。
     * 通常按 vendor name 匹配。
     */
    boolean supports(Vendor vendor);

    /**
     * 查询该供应商的余额。
     * @param vendor 完整供应商对象
     * @return 余额信息
     * @throws Exception 查询失败时抛出
     */
    BalanceInfo queryBalance(Vendor vendor) throws Exception;
}
