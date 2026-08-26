/**
 * 한국어 번역.
 * 기존 영어 사전을 안전한 대체 문구로 상속하고 사용자가 처음 접하는 공통 흐름을 한국어로 제공한다.
 */
import enUS from './en-US'

export default {
  ...enUS,
  common: {
    ...enUS.common,
    subtitle: '마이크로서비스 관리 플랫폼',
    login: '로그인', logout: '로그아웃', cancel: '취소', confirm: '확인', submit: '제출', loading: '불러오는 중...',
    success: '작업이 완료되었습니다', error: '작업에 실패했습니다', home: '홈', dashboard: '대시보드', console: '콘솔',
    create: '새로 만들기', edit: '편집', view: '보기', delete: '삭제', search: '검색', reset: '초기화', refresh: '새로 고침',
    save: '저장', back: '뒤로', close: '닫기', status: '상태', enabled: '사용', disabled: '사용 안 함', actions: '작업', total: '합계', items: '건', yes: '예', no: '아니요', sort: '정렬',
    systemManagement: '시스템 관리', users: '사용자 관리', roles: '역할 관리', permissions: '권한 관리', organizations: '조직 관리',
    tenants: '테넌트 관리', onlineUsers: '온라인 사용자', authRecords: '인증 기록', oauth2Clients: 'OAuth2 클라이언트', auditLogs: '감사 로그',
    xssConfig: 'XSS 보호', baseManagement: '기초 데이터', jobManagement: '작업 스케줄링', dictManagement: '사전 관리', operLogs: '작업 로그',
    monitorManagement: '운영 모니터링', mqMessages: '메시지 기록', userJobTypes: '작업 유형', systemJobs: '시스템 작업', workflowManagement: '워크플로 관리',
    workflowModel: '프로세스 모델', workflowDefinition: '프로세스 정의', workflowInstance: '프로세스 인스턴스', workflowStats: '통계 대시보드',
    crmManagement: '고객 관계 관리', crmOverview: '영업 개요', crmLeads: '리드 관리', crmCustomers: '고객 관리', crmContacts: '연락처 관리',
    crmOpportunities: '영업 기회 관리', crmActivities: '후속 활동', srmManagement: '공급업체 관계 관리', srmOverview: '공급업체 개요',
    srmSuppliers: '공급업체 관리', srmEvaluations: '성과 평가', srmRisk: '위험 관리', srmRiskConfig: '위험 지표 설정', srmPortal: '공급업체 포털',
    srmInvite: '초대 관리', supplierPortal: '공급업체 입구', srmPortalProfile: '기업 정보', srmPortalEvaluation: '성과 평가', srmPortalQuotation: '견적 응답',
    procurementManagement: '조달 실행 관리', procurementOverview: '조달 개요', procurementMaterial: '품목 카탈로그', procurementApprovalRoute: '구매 요청 승인 규칙',
    procurementRequisition: '구매 요청 관리', procurementRfq: '견적 요청 및 비교', procurementPurchaseOrder: '구매 주문', procurementGoodsReceipt: '입고 확인',
    assetManagement: '자산 관리', assetOverview: '자산 개요', assetLedger: '자산 원장', assetMyAssets: '내 자산', assetTransfer: '자산 이동', assetDisposal: '자산 처분',
  },
  lang: { zhCN: '简体中文', enUS: 'English', jaJP: '日本語', koKR: '한국어', switch: '언어 전환' },
  theme: { ...enUS.theme, toggle: '테마 전환' },
  login: {
    ...enUS.login,
    title: '인증 센터', username: '사용자 이름', password: '비밀번호', captcha: '보안 문자',
    captchaPlaceholder: '보안 문자 입력', refreshCaptcha: '클릭하여 보안 문자 새로 고침', tenant: '테넌트',
    tenantPlaceholder: '테넌트 선택', defaultTenant: '기본 테넌트', loginButton: '로그인',
    ssoLogin: '기업 SSO 로그인', ssoDesc: '기업 계정으로 로그인', deviceAuth: '기기 인증 로그인',
    oauth2ModeTitle: 'OAuth2 인증 로그인', oauth2ModeTip: '인증 후 요청한 애플리케이션으로 돌아갑니다.',
    thirdParty: '다른 로그인 방식', rememberMe: '로그인 상태 유지', forgotPassword: '비밀번호 찾기',
    noAccount: '계정이 없으신가요?', contactAdmin: '관리자에게 문의',
    loginSuccess: '로그인되었습니다', loginFailed: '로그인에 실패했습니다', captchaFailed: '보안 문자를 불러오지 못했습니다',
    captchaExpired: '보안 문자가 만료되었습니다. 새로 고침하세요', registerNow: '지금 가입',
  },
  register: {
    ...enUS.register,
    title: '계정 만들기', username: '사용자 이름', password: '비밀번호', confirmPassword: '비밀번호 확인',
    nickname: '별명(선택)', email: '이메일(선택)', captchaPlaceholder: '보안 문자 입력', registerButton: '가입',
    hasAccount: '이미 계정이 있으신가요?', goLogin: '로그인', registerSuccess: '가입되었습니다. 로그인해 주세요',
    passwordMismatch: '비밀번호가 일치하지 않습니다',
  },
  home: { ...enUS.home, welcome: 'Omni-Stack에 오신 것을 환영합니다', desc: 'AI와 Harness 패턴 기반 마이크로서비스 관리 플랫폼.', getStarted: '시작하기', goToConsole: '관리 콘솔로 이동' },
  device: {
    ...enUS.device,
    title: '기기 인증', requesting: '기기 인증을 요청하는 중...', userCodeLabel: '확인 코드',
    instruction: '다른 기기에서 아래 링크를 열고 확인 코드를 입력하여 인증하세요', waiting: '인증 대기 중...',
    expired: '확인 코드가 만료되었습니다', retry: '다시 시도', success: '기기 인증이 완료되었습니다',
    scanQrCode: 'QR 코드를 스캔하여 인증', orManual: '또는 아래 링크를 직접 여세요',
  },
  portalLogin: {
    ...enUS.portalLogin,
    title: '공급업체 포털 로그인', subtitle: '로그인하여 기업 정보를 관리하세요',
    noAccount: '공급업체 계정이 없으신가요?', registerNow: '지금 가입',
  },
}
