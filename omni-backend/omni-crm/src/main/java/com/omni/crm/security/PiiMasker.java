package com.omni.crm.security;

/**
 * CRM 后端 PII 掩码工具。
 *
 * @author Omni-Stack Team
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    /**
     * 掩码手机或电话号码。
     *
     * @param value 原值
     * @return 掩码值
     */
    public static String phone(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int length = value.length();
        if (length <= 4) {
            return "*".repeat(length);
        }
        int left = Math.min(3, length - 4);
        return value.substring(0, left) + "*".repeat(length - left - 4) + value.substring(length - 4);
    }

    /**
     * 掩码邮箱。
     *
     * @param value 原值
     * @return 掩码值
     */
    public static String email(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return value.substring(0, 1) + "***" + value.substring(at);
    }

    /**
     * 掩码地址。
     *
     * @param value 原值
     * @return 掩码值
     */
    public static String address(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return value.length() <= 6 ? "******" : value.substring(0, 6) + "******";
    }
}
