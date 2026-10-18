<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'
import { useData, useRoute } from 'vitepress'
import DefaultTheme from 'vitepress/theme'

const route = useRoute()
const { isDark } = useData()
const definitions = new WeakMap<HTMLElement, string>()
let generation = 0
let sequence = 0

/** 在客户端渲染规范 Markdown 中的图表，保留正文的 v-pre 保护。 */
async function renderDiagrams() {
  const currentGeneration = ++generation
  await nextTick()
  await document.fonts.ready
  if (currentGeneration !== generation) return

  const containers = [...document.querySelectorAll<HTMLElement>(
    '.vp-doc div.language-mermaid, .vp-doc .omni-mermaid',
  )]
  if (!containers.length) return

  const mermaid = (await import('mermaid')).default
  if (currentGeneration !== generation) return
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'strict',
    theme: isDark.value ? 'dark' : 'default',
    fontFamily: 'var(--vp-font-family-base)',
  })

  for (const container of containers) {
    const definition = definitions.get(container) ?? container.querySelector('code')?.textContent
    if (!definition || !container.isConnected) continue
    // 临时 SVG 限定在本次容器内，失败或页面切换时也能回收。
    const renderingHost = document.createElement('div')
    renderingHost.style.position = 'absolute'
    renderingHost.style.visibility = 'hidden'
    renderingHost.setAttribute('aria-hidden', 'true')
    document.body.append(renderingHost)
    try {
      const { svg } = await mermaid.render(`omni-mermaid-${++sequence}`, definition, renderingHost)
      if (currentGeneration !== generation || !container.isConnected) return

      const rendered = document.createElement('div')
      rendered.className = 'omni-mermaid mermaid'
      rendered.tabIndex = 0
      rendered.innerHTML = svg
      const graphic = rendered.querySelector('svg')
      if (!graphic) throw new Error('Mermaid returned no SVG')

      // 大图保留可读字号，由图表容器滚动，避免撑宽整个页面。
      const bounds = graphic.viewBox.baseVal
      if (bounds.width > 0 && bounds.height > 0) {
        graphic.setAttribute('width', String(bounds.width))
        graphic.setAttribute('height', String(bounds.height))
        graphic.style.maxWidth = 'none'
      }
      definitions.set(rendered, definition)
      container.replaceWith(rendered)
    } catch (error) {
      if (currentGeneration !== generation || !container.isConnected) return
      // 失败时保留原始代码，错误交给浏览器验证与维护者排查。
      container.dataset.diagramError = 'true'
      console.error('Mermaid diagram rendering failed', error)
    } finally {
      renderingHost.remove()
    }
  }
}

onMounted(() => { void renderDiagrams() })
watch([() => route.path, isDark], () => { void renderDiagrams() }, { flush: 'post' })
onBeforeUnmount(() => { generation++ })
</script>

<template>
  <DefaultTheme.Layout />
</template>

<style scoped>
:deep(.vp-doc .omni-mermaid) {
  max-width: 100%;
  margin: 16px 0;
  overflow: auto;
}

:deep(.vp-doc .omni-mermaid svg) {
  display: block;
}

:deep(.vp-doc .omni-mermaid:focus-visible) {
  outline: 2px solid var(--vp-c-brand-1);
  outline-offset: 2px;
}
</style>
