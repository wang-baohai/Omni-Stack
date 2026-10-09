/**
 * 四语静态盘点：四语「继承英文 / 相同字面」审计（只读）。
 * 目的：找出 ja-JP / ko-KR 中与 en-US 字面完全相同的用户可见值 —— key parity 绿灯无法发现的语义缺口。
 * 用法：node --experimental-strip-types scripts/locale-inheritance-audit.mjs
 * 说明：本脚本不替代官方 locale-parity-check --strict，而是对其未覆盖的「值层面」做独立交叉验证。
 * 依赖 ts-strip-resolver.mjs 处理语言包内部的无扩展名相对导入。
 */
import { register } from 'node:module'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

// 解析钩子必须先注册，因此语言包使用动态 import（静态 import 会被提升先于 register 执行）
register('./ts-strip-resolver.mjs', import.meta.url)

const { default: zhCN } = await import('../src/locales/zh-CN.ts')
const { default: enUS } = await import('../src/locales/en-US.ts')
const { default: jaJP } = await import('../src/locales/ja-JP.ts')
const { default: koKR } = await import('../src/locales/ko-KR.ts')

const HAN = /\p{Script=Han}/u
const KANA = /[\u3040-\u30ff]/u
const HANGUL = /[\uac00-\ud7a3]/u

/** 递归扁平化语言包为点分键 */
function flat(obj, prefix = '', out = {}) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k
    if (v && typeof v === 'object' && !Array.isArray(v)) flat(v, key, out)
    else out[key] = v
  }
  return out
}

const ZH = flat(zhCN), EN = flat(enUS), JA = flat(jaJP), KO = flat(koKR)
const keys = Object.keys(EN).sort()

const summary = {
  key_counts: { 'zh-CN': Object.keys(ZH).length, 'en-US': keys.length, 'ja-JP': Object.keys(JA).length, 'ko-KR': Object.keys(KO).length },
  missing_vs_en: { 'zh-CN': [], 'ja-JP': [], 'ko-KR': [] },
  extra_vs_en: { 'zh-CN': [], 'ja-JP': [], 'ko-KR': [] },
}
for (const k of keys) {
  for (const [loc, M] of [['zh-CN', ZH], ['ja-JP', JA], ['ko-KR', KO]]) if (!(k in M)) summary.missing_vs_en[loc].push(k)
}
for (const [loc, M] of [['zh-CN', ZH], ['ja-JP', JA], ['ko-KR', KO]]) {
  for (const k of Object.keys(M)) if (!(k in EN)) summary.extra_vs_en[loc].push(k)
}

/** 判断是否为纯技术值（URL / 权限码 / 纯占位符 / 数字），技术值不计入继承英文 */
function isTechnical(v) {
  const s = String(v)
  if (/^(https?:|\/|\d+$)/.test(s)) return true
  if (/^[a-z]+:[a-z_]+$/.test(s)) return true
  if (/^\{?\w+\}?$/.test(s) && !/[a-z]{3,}\s[a-z]{3,}/i.test(s)) return true
  return false
}

const identical = { 'ja-JP': [], 'ko-KR': [] }
const identicalByNs = { 'ja-JP': {}, 'ko-KR': {} }
for (const k of keys) {
  const en = EN[k]
  if (typeof en !== 'string' || en.trim() === '' || isTechnical(en)) continue
  for (const [loc, M] of [['ja-JP', JA], ['ko-KR', KO]]) {
    if (typeof M[k] === 'string' && M[k] === en) {
      identical[loc].push({ key: k, value: en })
      const ns = k.split('.')[0]
      identicalByNs[loc][ns] = (identicalByNs[loc][ns] || 0) + 1
    }
  }
}

const noNative = { 'ja-JP': [], 'ko-KR': [] }
for (const k of keys) {
  const checks = [
    ['ja-JP', JA, (s) => KANA.test(s) || HAN.test(s)],
    ['ko-KR', KO, (s) => HANGUL.test(s) || HAN.test(s)],
  ]
  for (const [loc, M, hasNative] of checks) {
    const v = M[k]
    if (typeof v !== 'string' || v.trim() === '' || isTechnical(v)) continue
    if (!hasNative(v)) noNative[loc].push({ key: k, value: v, also_same_as_en: v === EN[k] })
  }
}

const topEN = [...new Set(keys.map((k) => k.split('.')[0]))].sort()
const covered = (M) => new Set(Object.keys(M).map((k) => k.split('.')[0]))
const covZH = covered(ZH), covJA = covered(JA), covKO = covered(KO)
const namespaces = topEN.map((ns) => ({
  namespace: ns,
  en_leaves: keys.filter((k) => k.split('.')[0] === ns).length,
  zh_own: covZH.has(ns),
  ja_own: covJA.has(ns),
  ko_own: covKO.has(ns),
  ja_identical_to_en: identicalByNs['ja-JP'][ns] || 0,
  ko_identical_to_en: identicalByNs['ko-KR'][ns] || 0,
}))

function ph(v) { return [...String(v).matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',') }
const phMismatch = []
for (const k of keys) {
  const base = ph(EN[k])
  for (const [loc, M] of [['zh-CN', ZH], ['ja-JP', JA], ['ko-KR', KO]]) {
    if (typeof M[k] === 'string' && ph(M[k]) !== base) phMismatch.push({ key: k, loc, en: base, other: ph(M[k]) })
  }
}

const report = {
  schema: 'omni.locale-inheritance-audit/v1',
  generated_utc: new Date().toISOString(),
  method: 'node --experimental-strip-types 直接 import 四份 locale TS 源并扁平化比较（只读，独立交叉验证，不替代官方 strict checker）',
  summary,
  namespace_coverage: namespaces,
  identical_to_en_count: { 'ja-JP': identical['ja-JP'].length, 'ko-KR': identical['ko-KR'].length },
  identical_to_en: identical,
  no_native_script_count: { 'ja-JP': noNative['ja-JP'].length, 'ko-KR': noNative['ko-KR'].length },
  no_native_script: noNative,
  placeholder_mismatch_vs_en: phMismatch,
  caveat: '字面与 en 相同不等于错误（品牌名/技术词/缩写可能故意保留）；每个待修 token 须逐个记录中文源义与四语业务等价理由，不得据此清单批量改写。',
}
// 显式输出文件可放在仓库外；未指定时只输出报告，不生成阶段目录。
const outPath = process.argv[2]
if (outPath) {
  mkdirSync(resolve(outPath, '..'), { recursive: true })
  writeFileSync(outPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8')
  process.stdout.write(`locale inheritance audit written: ${outPath}\n`)
} else {
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
}
