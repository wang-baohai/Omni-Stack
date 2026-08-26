/**
 * 日本語翻訳。
 * 既存の英語辞書を安全なフォールバックとして継承し、利用者が最初に触れる共通導線を日本語化する。
 */
import enUS from './en-US'

export default {
  ...enUS,
  common: {
    ...enUS.common,
    subtitle: 'マイクロサービス管理プラットフォーム',
    login: 'ログイン', logout: 'ログアウト', cancel: 'キャンセル', confirm: '確認', submit: '送信',
    loading: '読み込み中...', success: '操作が完了しました', error: '操作に失敗しました', home: 'ホーム',
    dashboard: 'ダッシュボード', console: 'コンソール', create: '新規作成', edit: '編集', view: '表示',
    delete: '削除', search: '検索', reset: 'リセット', refresh: '更新', save: '保存', back: '戻る', close: '閉じる',
    status: '状態', enabled: '有効', disabled: '無効', actions: '操作', total: '合計', items: '件', yes: 'はい', no: 'いいえ', sort: '並び順',
    systemManagement: 'システム管理', users: 'ユーザー管理', roles: 'ロール管理', permissions: '権限管理',
    organizations: '組織管理', tenants: 'テナント管理', onlineUsers: 'オンラインユーザー', authRecords: '認証記録',
    oauth2Clients: 'OAuth2 クライアント', auditLogs: '監査ログ', xssConfig: 'XSS 防御', baseManagement: '基本データ',
    jobManagement: 'ジョブ管理', dictManagement: '辞書管理', operLogs: '操作ログ', monitorManagement: '運用監視',
    mqMessages: 'メッセージ記録', userJobTypes: 'ジョブ種別', systemJobs: 'システムジョブ', workflowManagement: 'ワークフロー管理',
    workflowModel: 'プロセスモデル', workflowDefinition: 'プロセス定義', workflowInstance: 'プロセスインスタンス', workflowStats: '統計ダッシュボード',
    crmManagement: '顧客関係管理', crmOverview: '営業概要', crmLeads: 'リード管理', crmCustomers: '顧客管理',
    crmContacts: '連絡先管理', crmOpportunities: '商談管理', crmActivities: '活動履歴', srmManagement: 'サプライヤー関係管理',
    srmOverview: 'サプライヤー概要', srmSuppliers: 'サプライヤー管理', srmEvaluations: '業績評価', srmRisk: 'リスク管理',
    srmRiskConfig: 'リスク指標設定', srmPortal: 'サプライヤーポータル', srmInvite: '招待管理', supplierPortal: 'サプライヤー入口',
    srmPortalProfile: '企業情報', srmPortalEvaluation: '業績評価', srmPortalQuotation: '見積回答', procurementManagement: '調達実行管理',
    procurementOverview: '調達概要', procurementMaterial: '品目カタログ', procurementApprovalRoute: '購買申請承認ルール',
    procurementRequisition: '購買申請管理', procurementRfq: '見積依頼・比較', procurementPurchaseOrder: '発注書', procurementGoodsReceipt: '入荷確認',
    assetManagement: '資産管理', assetOverview: '資産概要', assetLedger: '資産台帳', assetMyAssets: '自分の資産', assetTransfer: '資産移管', assetDisposal: '資産廃棄',
  },
  lang: { zhCN: '简体中文', enUS: 'English', jaJP: '日本語', koKR: '한국어', switch: '言語を切り替える' },
  theme: { ...enUS.theme, toggle: 'テーマを切り替える' },
  login: {
    ...enUS.login,
    title: '認証センター', username: 'ユーザー名', password: 'パスワード', captcha: '認証コード',
    captchaPlaceholder: '認証コードを入力', refreshCaptcha: 'クリックして認証コードを更新', tenant: 'テナント',
    tenantPlaceholder: 'テナントを選択', defaultTenant: '既定のテナント', loginButton: 'ログイン',
    ssoLogin: '企業 SSO ログイン', ssoDesc: '企業アカウントでログイン', deviceAuth: 'デバイス認証ログイン',
    oauth2ModeTitle: 'OAuth2 認証ログイン', oauth2ModeTip: '認証後、要求元のアプリケーションへ戻ります。',
    thirdParty: 'その他のログイン方法', rememberMe: 'ログイン状態を保持', forgotPassword: 'パスワードを忘れた場合',
    noAccount: 'アカウントをお持ちでない場合', contactAdmin: '管理者に連絡',
    loginSuccess: 'ログインしました', loginFailed: 'ログインに失敗しました', captchaFailed: '認証コードの読み込みに失敗しました',
    captchaExpired: '認証コードの有効期限が切れました。更新してください', registerNow: '今すぐ登録',
  },
  register: {
    ...enUS.register,
    title: 'アカウント作成', username: 'ユーザー名', password: 'パスワード', confirmPassword: 'パスワード確認',
    nickname: '表示名（任意）', email: 'メールアドレス（任意）', captchaPlaceholder: '認証コードを入力',
    registerButton: '登録', hasAccount: 'すでにアカウントをお持ちですか？', goLogin: 'ログイン',
    registerSuccess: '登録しました。ログインしてください', passwordMismatch: 'パスワードが一致しません',
  },
  home: { ...enUS.home, welcome: 'Omni-Stack へようこそ', desc: 'AI と Harness パターンを活用したマイクロサービス管理基盤。', getStarted: 'はじめる', goToConsole: '管理コンソールへ' },
  device: {
    ...enUS.device,
    title: 'デバイス認証', requesting: 'デバイス認証を要求しています...', userCodeLabel: '確認コード',
    instruction: '別のデバイスで次のリンクを開き、確認コードを入力して認証してください', waiting: '認証を待っています...',
    expired: '確認コードの有効期限が切れました', retry: '再試行', success: 'デバイス認証が完了しました',
    scanQrCode: 'QR コードを読み取って認証', orManual: 'または次のリンクを手動で開く',
  },
  portalLogin: {
    ...enUS.portalLogin,
    title: 'サプライヤーポータル ログイン', subtitle: 'ログインして企業情報を管理します',
    noAccount: 'サプライヤーアカウントをお持ちでない場合', registerNow: '今すぐ登録',
  },
}
