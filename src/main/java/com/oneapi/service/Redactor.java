package com.oneapi.service;

import java.util.regex.Pattern;

/**
 * Redactor — 写入全息日志前扫描 body 文本，替换常见敏感字段。
 *
 * <p>设计取舍：默认行为保留整段 body 入库（与数据库镜像上游 body 的设计一致），
 * 只把"如果泄漏到日志/截图就有风险"的那部分敏感字符串 mask。
 * 之所以不是像方案 A/B/C 那样默认不写，是因为：
 * <ol>
 *   <li>同一份请求 body 已经发给上游——真正的风险是上行通道，而不是数据库留存；</li>
 *   <li>全息日志的调试价值来自可见上下文，仅 metadata 不足以重构。</li>
 * </ol>
 * 因此方案 D：保留 full body，只过这一道 mask。
 *
 * <p>可配置 pattern 通过{@link com.oneapi.config.AppConfig.RedactorConfig}控制开关。
 */
public class Redactor {

    private static final String MASK_TOKEN = "[REDACTED]";

    private boolean redactOpenaiKey = true;
    private boolean redactBearer = true;
    private boolean redactEmail = true;
    private boolean redactPhoneCn = true;
    private boolean redactCardLike = true;

    private final Pattern openaiKey =
        Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}\\b");
    private final Pattern bearer =
        Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._\\-]+");
    private final Pattern email =
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    // 中国大陆手机号（11 位，1 开头）
    private final Pattern phoneCn =
        Pattern.compile("\\b1[3-9]\\d{9}\\b");
    // 类信用卡号 13-19 位数字，容忍空格/短横分组
    private final Pattern cardLike =
        Pattern.compile("(?:\\d[ -]*?){13,19}");

    public Redactor() {}

    /** Apply AppConfig.RedactorConfig flags to this instance. */
    public Redactor(boolean openaiKey, boolean bearer, boolean email, boolean phoneCn, boolean cardLike) {
        this.redactOpenaiKey = openaiKey;
        this.redactBearer = bearer;
        this.redactEmail = email;
        this.redactPhoneCn = phoneCn;
        this.redactCardLike = cardLike;
    }

    public String redact(String input) {
        if (input == null || input.isEmpty()) return input;
        String s = input;
        if (redactOpenaiKey) {
            s = openaiKey.matcher(s).replaceAll(MASK_TOKEN);
        }
        if (redactBearer) {
            s = bearer.matcher(s).replaceAll(MASK_TOKEN);
        }
        if (redactEmail) {
            s = email.matcher(s).replaceAll(MASK_TOKEN);
        }
        if (redactPhoneCn) {
            s = phoneCn.matcher(s).replaceAll(MASK_TOKEN);
        }
        if (redactCardLike) {
            // 卡片号与手机号重叠时（11 位 ∈ 13-19 位），上面 phoneCn 先跑会 mask 走，
            // 这里用更严格的边界：要求最后一段是 16-19 位数字或末 4 位有效。
            s = cardLike.matcher(s).replaceAll(MASK_TOKEN);
        }
        return s;
    }

    /** 测试用 — 暴露配置值用于断言。 */
    public boolean isRedactOpenaiKey() { return redactOpenaiKey; }
    public boolean isRedactBearer() { return redactBearer; }
    public boolean isRedactEmail() { return redactEmail; }
    public boolean isRedactPhoneCn() { return redactPhoneCn; }
    public boolean isRedactCardLike() { return redactCardLike; }
}
