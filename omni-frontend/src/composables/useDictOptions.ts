/**
 * @module composables/useDictOptions
 * 按租户缓存字典选项，并隔离身份切换期间的在途响应。
 */
import { reactive, computed, ref, watch, effectScope, type ComputedRef, type Ref } from 'vue'
import { getDictOptions, type DictOption } from '@/api/dict'
import { useUserStore } from '@/stores/user'
import { getTenantIdFromToken } from '@/utils/jwt'

/** 响应式缓存：租户 ID 与 typeCode 共同确定选项归属。 */
const store = reactive<Record<string, DictOption[]>>({})

/** 同一身份上下文中的在途请求。 */
interface PendingDictRequest {
  token: string
  valid: boolean
  promise: Promise<DictOption[]>
}

const pendingMap = new Map<string, PendingDictRequest>()

let observedStore: ReturnType<typeof useUserStore> | null = null
let contextScope: ReturnType<typeof effectScope> | null = null

/** 清除会话缓存，并阻止已退出上下文的请求回填。 */
function clearSessionCache() {
  for (const key of Object.keys(store)) delete store[key]
  for (const request of pendingMap.values()) request.valid = false
  pendingMap.clear()
}

/** 模块缓存的身份观察独立于页面消费者生命周期。 */
function observeContext(userStore: ReturnType<typeof useUserStore>) {
  if (observedStore === userStore) return
  contextScope?.stop()
  clearSessionCache()
  observedStore = userStore
  contextScope = effectScope(true)
  contextScope.run(() => watch(() => userStore.token, (token) => {
    for (const request of pendingMap.values()) {
      if (request.token !== token) request.valid = false
    }
    if (!getTenantIdFromToken(token)) clearSessionCache()
  }, { immediate: true, flush: 'sync' }))
}

import.meta.hot?.dispose(() => {
  contextScope?.stop()
  clearSessionCache()
})

/**
 * 获取当前租户的字典选项，租户或身份变化时立即隔离旧响应。
 *
 * @param typeCode 字典类型编码
 * @returns 当前租户选项与响应式加载状态
 */
export function useDictOptions(typeCode: string): {
  options: ComputedRef<DictOption[]>
  loading: Ref<boolean>
} {
  const userStore = useUserStore()
  observeContext(userStore)
  const loading = ref(false)
  const cacheKey = computed(() => {
    const tenantId = getTenantIdFromToken(userStore.token)
    return tenantId ? `${tenantId}:${typeCode}` : null
  })

  watch(() => userStore.token, (token, _previous, onCleanup) => {
    const key = cacheKey.value
    if (!key) {
      loading.value = false
      return
    }
    if (store[key]) {
      loading.value = false
      return
    }

    let pending = pendingMap.get(key)
    if (!pending || !pending.valid || pending.token !== token) {
      const request: PendingDictRequest = {
        token,
        valid: true,
        promise: getDictOptions(typeCode)
          .then((res) => {
            const data = res.data.data || []
            if (request.valid && userStore.token === token) store[key] = data
            return data
          })
          .finally(() => {
            if (pendingMap.get(key) === request) pendingMap.delete(key)
          }),
      }
      pendingMap.set(key, request)
      pending = request
    }

    const request = pending
    let active = true
    loading.value = true
    onCleanup(() => {
      active = false
      if (userStore.token !== request.token) request.valid = false
    })
    const finish = () => {
      if (active) loading.value = false
    }
    void request.promise.then(finish, finish)
  }, { immediate: true, flush: 'sync' })

  return {
    options: computed(() => cacheKey.value ? store[cacheKey.value] ?? [] : []),
    loading,
  }
}
