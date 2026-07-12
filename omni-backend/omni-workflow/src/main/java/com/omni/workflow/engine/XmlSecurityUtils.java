package com.omni.workflow.engine;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;

/**
 * XML 安全工具类，统一提供防 XXE 攻击的解析器工厂配置。
 * <p>
 * 所有需要解析 XML 的代码都应使用本工具类创建工厂实例，
 * 而非直接调用 {@code DocumentBuilderFactory.newInstance()}。
 * </p>
 *
 * @author Omni-Stack Team
 */
public final class XmlSecurityUtils {

    private XmlSecurityUtils() {
        // 工具类禁止实例化
    }

    /**
     * 创建安全的 {@link DocumentBuilderFactory}，已禁用外部实体和 DOCTYPE 声明。
     * <p>
     * 防护配置：
     * <ul>
     *   <li>{@code disallow-doctype-decl = true} — 禁止 DOCTYPE 声明</li>
     *   <li>{@code external-general-entities = false} — 禁止外部通用实体</li>
     *   <li>{@code external-parameter-entities = false} — 禁止外部参数实体</li>
     * </ul>
     * </p>
     *
     * @return 已配置 XXE 防护的 DocumentBuilderFactory 实例
     */
    public static DocumentBuilderFactory createSafeDocumentBuilderFactory() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            return factory;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建安全的 DocumentBuilderFactory", e);
        }
    }

    /**
     * 创建安全的 {@link TransformerFactory}，已禁止外部 DTD 和样式表访问。
     *
     * @return 已配置安全防护的 TransformerFactory 实例
     */
    public static TransformerFactory createSafeTransformerFactory() {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        return tf;
    }
}
