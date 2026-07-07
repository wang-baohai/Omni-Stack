/**
 * @module composables/useDictOptions
 * 字典选项 composable，用于前端下拉组件。
 * <p>按 typeCode 缓存字典选项，多组件共享同一份响应式数据，不重复请求。</p>
 */
import { reactive, computed, type ComputedRef } from 'vue'
import { getDictOptions, type DictOption } from '@/api/dict'

/** 响应式缓存：typeCode → 选项列表（所有消费者共享） */
const store = reactive<Record<string, DictOption[]>>({})

/** 模块级加载 Promise：typeCode → 正在进行的请求（防止并发重复请求） */
const pendingMap = new Map<string, Promise<DictOption[]>>()

/**
 * 获取字典选项。
 * <p>首次调用时从后端加载，之后命中模块级缓存。多组件共享同一份响应式缓存，
 * 并发调用复用同一个 Promise，不会出现竞态导致部分组件拿到空数据。</p>
 *
 * @param typeCode - 字典类型编码
 * @returns options 响应式选项列表（computed），loading 响应式加载状态
 */
export function useDictOptions(typeCode: string): {
  options: ComputedRef<DictOption[]>
  loading: { value: boolean }
} {
  const loading = { value: false }

  // 已有缓存，直接返回
  if (store[typeCode]) {
    return { options: computed(() => store[typeCode] ?? []), loading }
  }

  // 已有正在进行的请求，复用 Promise
  let pending = pendingMap.get(typeCode)

  if (!pending) {
    // 首次加载
    loading.value = true
    pending = getDictOptions(typeCode)
      .then((res) => {
        const data = res.data.data || []
        store[typeCode] = data
        return data
      })
      .finally(() => {
        pendingMap.delete(typeCode)
      })
    pendingMap.set(typeCode, pending)
  }

  // 等待加载完成后更新 loading 状态
  pending.finally(() => {
    loading.value = false
  })

  return { options: computed(() => store[typeCode] ?? []), loading }
}
