# 3. 框架与源码：授权服务器怎样把协议变成检查

上一篇已经解释授权码和 PKCE。这一篇再打开框架内部：请求进入后由谁接手，服务器怎样比较交换材料，以及为什么同一个授权码不能一直使用。

本项目的 Spring Boot 4.0.6 管理 Spring Security 7.0.5；授权服务器工件也采用 7.0.5。下面引用这个实际版本的连续原文。你可以先读代码前后的说明，再回头看变量，不必一次记住类名。

## 为什么使用框架，而不把协议全写在控制器里

做一个登录表单和维护一套授权协议，工作量并不相同。后者还要处理客户端登记、回调地址、授权码、交换参数、令牌格式和不同错误。每一处条件都可能影响其他应用怎样接入。

从当前工程可以看到三种方案的取舍：

| 方案 | 需要承担什么 | 与当前项目的关系 |
|---|---|---|
| 自己编写协议端点 | 自行维护协议规则、安全校验、兼容性和测试 | 项目没有用这种方式重写本地授权码协议 |
| 在 Spring 应用中使用安全与授权服务器框架 | 复用协议处理器，维护自己的客户端、登录衔接、密钥和业务权限 | 当前采用 Spring Security 与其授权服务器模块 |
| 把身份认证集中到独立身份平台 | 另行维护身份平台及它与业务账号、租户、角色的衔接 | 当前标准本地环境没有用它替代内置 Auth 服务 |

这张表解释源码能够支持的设计取舍，不是虚构一次历史评审。项目已有 Spring Boot 服务，也需要自己的用户、租户和权限模型，把协议端点接入同一套 Spring 安全体系能够减少重复实现；业务身份和权限仍由项目负责。

Spring Security 提供安全过滤链和认证处理机制。授权服务器模块使用这些机制处理 OAuth2/OIDC 协议。它不是数据库里的角色表，也不会替项目决定“小李能不能审批这张单据”。

## 第一段：请求先变成结构化对象，再交给处理器

浏览器传来的是请求参数。框架先把参数整理成内部能处理的对象，再交给认证管理器。授权端点过滤器中的连续原文如下。

原文件：OAuth2AuthorizationEndpointFilter.java，第 193–201 行，Spring Security 7.0.5。

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/authorization-request-dispatch.java.txt" -->
```java
			Authentication authentication = (Authentication) request
				.getAttribute(OAuth2AuthorizationCodeRequestAuthenticationToken.class.getName());
			if (authentication == null) {
				authentication = this.authenticationConverter.convert(request);
				if (authentication instanceof AbstractAuthenticationToken authenticationToken) {
					authenticationToken.setDetails(this.authenticationDetailsSource.buildDetails(request));
				}
			}
			Authentication authenticationResult = this.authenticationManager.authenticate(authentication);
```

前两行先看前面的校验环节是否已经准备好请求对象。如果没有，就由 converter 解析当前请求；随后补充请求细节，交给 authenticationManager 继续处理。

这里的 Authentication 是框架内部对象的共同类型，不能只凭变量名就断言“用户密码已经验证成功”。解析请求和完成认证是两个阶段，相应处理器还会检查客户端、请求参数和当前用户等条件。

在项目里，授权服务器端点先被专门的安全过滤链匹配，再进入这些框架处理器。普通密码登录则有自己的项目控制器，后面会单独说明。

## 第二段：PKCE 的原件怎样与摘要对应上

公有客户端无法把一个长期密码安全地藏在浏览器代码中。对授权码交换，框架的公有客户端认证处理器调用 codeVerifierAuthenticator.authenticateRequired，再由下面的方法比较 PKCE 材料。

原文件：CodeVerifierAuthenticator.java，第 151–170 行，Spring Security 7.0.5。

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/pkce-s256.java.txt" -->
```java
	private boolean codeVerifierValid(String codeVerifier, String codeChallenge, String codeChallengeMethod) {
		if (!StringUtils.hasText(codeVerifier)) {
			return false;
		}
		else if ("S256".equals(codeChallengeMethod)) {
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
				String encodedVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
				return encodedVerifier.equals(codeChallenge);
			}
			catch (NoSuchAlgorithmException ex) {
				// It is unlikely that SHA-256 is not available on the server. If it is
				// not available,
				// there will likely be bigger issues as well. We default to SERVER_ERROR.
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
			}
		}
		return false;
	}
```

先看开头：没有原件，检查失败。接着看 S256 分支：把原件按 SHA-256 计算摘要，转成不带填充的 base64url 编码，再与开始时保存的摘要比较。内容一致，才返回成功。

这个方法对其他未识别的方式返回失败。中间的异常分支则说明：如果服务器连所需的摘要算法都不可用，框架会报告服务器错误，而不是放行请求。

项目默认浏览器客户端要求 PKCE。前端生成随机原件，再用 Web Crypto 计算同样的摘要；原件留到回调换令牌时使用。这两侧计算方式需要一致。只换一下参数名字，不能代替这次真正的比较。

PKCE 并不把浏览器变成“能够保守长期秘密的服务器”。如果网页本身遭到恶意脚本控制，仍需单独处理；这也是为什么不能省去页面安全、回调地址限制和其他校验。

## 第三段：正常换令牌之后，把授权码作废

完成令牌生成后，框架还要记录这张授权码已经用过。下面引用的是正常处理路径，不是错误客户端请求时的拒绝分支。

原文件：OAuth2AuthorizationCodeAuthenticationProvider.java，第 294–299 行，Spring Security 7.0.5。

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/authorization-code-consumption.java.txt" -->
```java
		// Invalidate the authorization code as it can only be used once
		authorizationBuilder.invalidate(authorizationCode.getToken());

		authorization = authorizationBuilder.build();

		this.authorizationService.save(authorization);
```

第一句使授权码失效，后面构造并保存更新后的授权记录。因此，授权码不是可以反复拿来换票的长期密码。框架还有授权码有效性和重复使用检查，失败后需要按原应用入口重新开始，不能不停重发旧交换请求。

访问令牌的有效期与授权码是否已经消费，也是两件事。上面这一段不表示项目里所有 JWT 都在同一时刻被撤销；令牌、退出和业务权限的边界放在本章最后一篇说明。

## 框架和工程各负责哪一段

| 责任 | 这里由谁承担 | 下一步看哪里 |
|---|---|---|
| 解析 OAuth2 请求、检查交换材料、处理授权码 | Spring 授权服务器处理器 | 本页三个片段 |
| 怎样验证本地账号与验证码 | 项目 Auth 控制器及服务 | [普通密码登录](authentication-password.md) |
| 浏览器怎样建立会话、保存本次 PKCE 材料和处理回调 | 项目认证配置与前端 | [企业 SSO](authentication-oauth2.md) |
| 设备、第三方账号、注册和供应商入口怎样衔接 | 项目的相应入口及适配代码 | [设备与社交登录](authentication-device-social.md) |
| 菜单、接口、数据范围和退出后状态 | 网关、业务服务与前端各自的规则 | [权限与排障](authentication-security.md) |

采用框架减少了自己维护协议细节的工作，但客户端如何登记、会话怎样衔接、密钥怎样保存，仍需项目配置正确。源码中声明一个端点，也不等于真实第三方账号已经完成授权实联。

## 需要查请求地址时，按责任查

先理解上面的分工，再使用这张地址索引。它来自当前框架默认端点和项目入口，不是逐端点实测通过的清单。

| 地址 | 主要责任 |
|---|---|
| /api/auth/login | 项目普通密码登录，成功后返回本地登录结果 |
| /api/auth/session-login | 项目会话登录，为授权服务器建立当前用户状态 |
| /oauth2/authorize | 授权服务器处理授权请求和授权码发放 |
| /oauth2/token | 按相应许可完成令牌交换 |
| /oauth2/jwks | 提供签名验证所需的公开密钥材料，不提供私钥 |
| /oauth2/device_authorization | 开始设备授权，取得设备码和用户短码 |
| /oauth2/device_verification | 处理用户对设备短码的确认 |

普通登录接口与 OAuth2 协议接口的响应结构不同。前者使用项目统一的结果包裹，后者使用协议中的 access_token、error 等字段；前端应按对应协议解析，不能把它们当作同一种返回值。

## 原文与许可

上游片段来自 [7.0.5 源码包](https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-oauth2-authorization-server/7.0.5/spring-security-oauth2-authorization-server-7.0.5-sources.jar)，保留原文及 Copyright 2004-present the original author or authors 声明，采用 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)。来源登记记录包摘要、原文件摘要、行范围和片段摘要；片段只用于讲解，不是额外的运行依赖。

授权服务器功能在 Spring Security 7 中的归属，可查阅 [Spring 官方迁移说明](https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/)。本文始终使用项目的 7.0.5 原文，没有用较新网页中的 API 替换当前版本。

完整[许可证](../reference/upstream/spring-security-7.0.5/LICENSE.txt)和[来源登记](../reference/upstream/spring-security-7.0.5/provenance.json)随本站提供，无需访问外部仓库。
