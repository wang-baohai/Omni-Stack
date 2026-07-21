package com.omni.srm.service.support;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 供应商名称规范化工具。
 *
 * @author Omni-Stack Team
 */
public final class SupplierNameNormalizer {

    private SupplierNameNormalizer() {
    }

    /**
     * 生成用于检索和去重的规范化名称。
     *
     * @param name 原始供应商名称
     * @return NFKC、空白折叠并转小写后的名称
     */
    public static String normalize(String name) {
        if (name == null) {
            return null;
        }
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
