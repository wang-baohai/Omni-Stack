package com.omni.procurement.service.support;

import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * 请购审批规则技术编码生成器。
 *
 * @author Omni-Stack Team
 */
@Component
public class ApprovalRouteCodeGenerator {

    private static final char[] CROCKFORD_BASE32 =
            "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int ULID_LENGTH = 26;
    private static final int RANDOM_BYTES = 10;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 生成以 APR- 开头的标准 ULID 技术编码。
     *
     * @return 审批规则技术编码
     */
    public String generate() {
        byte[] value = new byte[16];
        long timestamp = System.currentTimeMillis();
        for (int index = 5; index >= 0; index--) {
            value[index] = (byte) timestamp;
            timestamp >>>= 8;
        }
        byte[] random = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(random);
        System.arraycopy(random, 0, value, 6, RANDOM_BYTES);
        return "APR-" + encode(value);
    }

    private String encode(byte[] value) {
        BigInteger remaining = new BigInteger(1, value);
        BigInteger radix = BigInteger.valueOf(32);
        char[] encoded = new char[ULID_LENGTH];
        for (int index = ULID_LENGTH - 1; index >= 0; index--) {
            BigInteger[] division = remaining.divideAndRemainder(radix);
            encoded[index] = CROCKFORD_BASE32[division[1].intValue()];
            remaining = division[0];
        }
        return new String(encoded);
    }
}
