/**
 * WP-012 辅助：为 --experimental-strip-types 提供无扩展名相对导入的 ESM 解析钩子。
 * 语言包内部使用 `import enUS from './en-US'`（Vite 风格无扩展名），Node 原生 ESM 要求带扩展名，
 * 故在此把解析失败的相对路径补上 .ts 再重试。仅用于只读盘点脚本，不影响构建产物。
 */
export async function resolve(specifier, context, nextResolve) {
  try {
    return await nextResolve(specifier, context)
  } catch (error) {
    if (specifier.startsWith('.') && !/\.[a-z]+$/i.test(specifier)) {
      return nextResolve(`${specifier}.ts`, context)
    }
    throw error
  }
}
