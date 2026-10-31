# 3. 프레임워크와 소스: 프로토콜을 실제 검사로 바꾸기

앞 글에서는 인가 코드와 PKCE를 설명했습니다. 이제 내부를 열어 요청을 누가 받는지, 교환 자료를 어떻게 비교하는지, 같은 코드를 계속 쓸 수 없는 이유를 봅니다.

프로젝트의 Spring Boot 4.0.6은 Spring Security 7.0.5를 관리하며 인가 서버 아티팩트도 7.0.5입니다. 아래는 이 버전의 연속된 원문입니다. 앞뒤 설명을 먼저 읽고 변수를 다시 보세요. 클래스 이름을 한 번에 외울 필요는 없습니다.

## 프로토콜을 컨트롤러에 직접 쓰지 않는 이유

로그인 폼을 만드는 일과 인가 프로토콜을 유지하는 일은 다릅니다. 후자에는 클라이언트 등록, 콜백 주소, 코드, 교환 매개변수, 토큰 형식, 오류 처리도 필요합니다. 각 조건이 다른 앱의 연동 방식에 영향을 줍니다.

현재 소스에서 확인할 수 있는 선택은 다음과 같습니다.

| 방식 | 감당해야 할 일 | 현재 프로젝트와의 관계 |
|---|---|---|
| 프로토콜 엔드포인트 직접 구현 | 규칙, 보안 검사, 호환성, 테스트 유지 | 로컬 인가 코드 프로토콜을 이 방식으로 다시 쓰지 않음 |
| Spring 안에서 보안·인가 서버 프레임워크 사용 | 프로토콜 처리기를 재사용하고 클라이언트·로그인 연결·키·업무 권한 관리 | Spring Security와 인가 서버 모듈 사용 |
| 별도 신원 플랫폼으로 집중 | 플랫폼 운영 및 업무 계정·테넌트·역할 매핑 | 표준 로컬 환경에서 내장 Auth를 대체하지 않음 |

소스가 뒷받침하는 설계의 절충을 설명한 표이며 과거 검토 회의를 만들어 낸 것은 아닙니다. 프로젝트는 이미 Spring Boot를 쓰고 자체 사용자·테넌트·권한 모델이 필요합니다. 같은 보안 체계에 프로토콜을 연결하면 중복 구현을 줄이지만, 업무 신원과 권한은 여전히 프로젝트 책임입니다.

Spring Security는 보안 필터 체인과 인증 장치를 제공합니다. 인가 서버 모듈은 이를 이용해 OAuth2/OIDC를 처리합니다. 역할 테이블 자체도 아니고, 리 씨가 특정 신청을 승인할지 결정하는 기능도 아닙니다.

## 첫 원문: 요청을 객체로 해석한 뒤 처리기로 넘기기

브라우저가 보낸 것은 매개변수입니다. 프레임워크는 이를 내부 객체로 정리한 다음 인증 관리자에게 넘깁니다. 인가 엔드포인트 필터의 연속된 원문입니다.

원본: OAuth2AuthorizationEndpointFilter.java, 193–201행, Spring Security 7.0.5.

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

첫 부분은 앞선 검사에서 요청 객체를 준비했는지 확인합니다. 없으면 converter가 현재 요청을 해석합니다. 요청 상세 정보를 붙인 뒤 authenticationManager가 처리합니다.

Authentication은 프레임워크 내부 공통 타입입니다. 변수 이름만 보고 사용자 비밀번호가 이미 검증되었다고 판단해서는 안 됩니다. 해석과 인증은 별도 단계이며, 해당 처리기가 클라이언트·매개변수·현재 사용자 등의 조건을 계속 검사합니다.

프로젝트에서는 전용 보안 체인이 인가 서버 엔드포인트를 먼저 선택하고 이 처리기로 들어갑니다. 일반 비밀번호 로그인은 자체 컨트롤러가 담당하며 별도 글에서 설명합니다.

## 두 번째 원문: PKCE 원본과 다이제스트 비교하기

공개 클라이언트는 브라우저 코드에 장기 비밀을 안전하게 숨길 수 없습니다. 인가 코드 교환 시 프레임워크의 공개 클라이언트 처리기가 codeVerifierAuthenticator.authenticateRequired를 호출하고, 아래 메서드가 PKCE 자료를 비교합니다.

원본: CodeVerifierAuthenticator.java, 151–170행, Spring Security 7.0.5.

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

원본이 없으면 실패합니다. S256 분기는 원본을 SHA-256으로 계산하고 패딩 없는 base64url로 인코딩한 뒤 기존 다이제스트와 비교합니다. 같아야 성공입니다.

이 메서드가 알지 못하는 방식은 실패합니다. 필요한 해시 알고리즘을 서버에서 사용할 수 없다면 예외 분기가 서버 오류를 내며 통과시키지 않습니다.

기본 브라우저 클라이언트는 PKCE가 필수입니다. 프런트는 무작위 원본을 만들고 Web Crypto로 같은 다이제스트를 계산하며, 콜백 교환 때까지 원본을 보관합니다. 양쪽 계산이 일치해야 합니다. 매개변수 이름 변경으로 실제 비교를 대신할 수 없습니다.

PKCE가 브라우저를 장기 비밀을 보관할 수 있는 서버로 바꾸지는 않습니다. 페이지가 악성 스크립트에 제어되는 문제는 별도로 다뤄야 하며 콜백 제한과 다른 검사도 필요합니다.

## 세 번째 원문: 정상 교환 후 코드를 무효화하기

토큰 생성 뒤에는 이 코드가 이미 사용되었다는 기록을 남깁니다. 잘못된 클라이언트를 거부하는 분기가 아닌 정상 처리 경로입니다.

원본: OAuth2AuthorizationCodeAuthenticationProvider.java, 294–299행, Spring Security 7.0.5.

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/authorization-code-consumption.java.txt" -->
```java
		// Invalidate the authorization code as it can only be used once
		authorizationBuilder.invalidate(authorizationCode.getToken());

		authorization = authorizationBuilder.build();

		this.authorizationService.save(authorization);
```

첫 문장이 코드를 무효화하고, 뒤에서 변경된 인가 기록을 만들고 저장합니다. 코드는 반복 교환할 수 있는 장기 비밀번호가 아닙니다. 프레임워크에는 유효성과 재사용 검사도 있습니다. 실패하면 오래된 교환 요청을 반복하지 말고 앱 진입점에서 다시 시작합니다.

접근 토큰의 유효 기간과 인가 코드 소비는 별개입니다. 위 코드가 프로젝트의 모든 JWT를 동시에 취소하지는 않습니다. 토큰, 로그아웃, 업무 권한은 마지막 글에서 설명합니다.

## 프레임워크와 프로젝트의 역할

| 책임 | 담당 | 이어 볼 글 |
|---|---|---|
| OAuth2 요청 해석, 교환 자료 검사, 코드 처리 | Spring 인가 서버 처리기 | 위 세 원문 |
| 로컬 계정과 CAPTCHA 검증 | 프로젝트 Auth 컨트롤러와 서비스 | [일반 비밀번호 로그인](authentication-password.kr.md) |
| 브라우저 세션, PKCE 자료 보관, 콜백 | 프로젝트 보안 설정과 프런트 | [기업 SSO](authentication-oauth2.kr.md) |
| 기기·외부 계정·가입·포털 연결 | 프로젝트 진입점과 어댑터 | [기기와 소셜 로그인](authentication-device-social.kr.md) |
| 메뉴·API 권한·데이터 범위·로그아웃 상태 | 게이트웨이·업무 서비스·프런트 규칙 | [권한과 문제 해결](authentication-security.kr.md) |

프레임워크가 프로토콜 유지 부담을 줄여도 클라이언트 등록, 세션 연결, 키 보관은 올바르게 설정해야 합니다. 소스에 엔드포인트가 있다는 사실이 실제 외부 계정 인가 성공을 증명하지는 않습니다.

## 주소가 필요하면 책임에 따라 찾기

역할을 이해한 뒤 사용하는 색인입니다. 현재 프레임워크 기본 주소와 프로젝트 진입점이며, 모든 엔드포인트의 실제 테스트 합격 목록은 아닙니다.

| 주소 | 주요 책임 |
|---|---|
| /api/auth/login | 프로젝트 일반 비밀번호 로그인, 로컬 로그인 결과 반환 |
| /api/auth/session-login | 인가 서버를 위한 현재 사용자 상태 생성 |
| /oauth2/authorize | 인가 요청과 코드 발급 |
| /oauth2/token | 해당 그랜트에 따른 토큰 교환 |
| /oauth2/jwks | 검증용 공개키 제공, 개인키는 제공하지 않음 |
| /oauth2/device_authorization | 기기 인가 시작, 기기 코드와 사용자 짧은 코드 획득 |
| /oauth2/device_verification | 사용자의 짧은 코드 확인 처리 |

일반 로그인은 프로젝트 결과 래퍼를 사용하고, 프로토콜 응답은 access_token, error 같은 필드를 사용합니다. 같은 응답으로 취급하지 말고 각 계약에 맞게 해석해야 합니다.

## 원문과 라이선스

발췌는 [7.0.5 소스 아카이브](https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-oauth2-authorization-server/7.0.5/spring-security-oauth2-authorization-server-7.0.5-sources.jar)에서 왔습니다. 원문 및 Copyright 2004-present the original author or authors 고지를 유지하며 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)을 따릅니다. 출처 기록은 아카이브·원본 파일·행 범위·발췌 다이제스트를 연결합니다. 설명용 인용이며 런타임 의존성을 추가한 것이 아닙니다.

Spring Security 7 통합은 [Spring 공식 발표](https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/)에서 볼 수 있습니다. 본문은 프로젝트의 7.0.5 원문을 사용하며 더 최신 웹 예제의 API로 바꾸지 않았습니다.

전체 [라이선스](../reference/upstream/spring-security-7.0.5/LICENSE.txt)와 [출처 기록](../reference/upstream/spring-security-7.0.5/provenance.json)은 이 사이트에 함께 제공됩니다.
