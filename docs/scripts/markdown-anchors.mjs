/** 与仓库 Markdown 链接使用同一标题规则，数字开头不添加额外下划线。 */
export function markdownSlug(title) {
  return title.trim().toLowerCase().replace(/[`*_~]/g, '')
    .replace(/[^\p{L}\p{N}\s-]/gu, '').replace(/\s/g, '-')
}

export function markdownAnchors(content) {
  const anchors = new Set(), counts = new Map()
  let fence = null
  for (const line of content.split(/\r?\n/)) {
    const delimiter = /^\s*(`{3,}|~{3,})/.exec(line)
    if (delimiter) {
      if (!fence) fence = delimiter[1][0]
      else if (fence === delimiter[1][0]) fence = null
      continue
    }
    if (fence) continue
    const heading = /^(#{1,6})\s+(.+?)\s*$/.exec(line)
    if (!heading) continue
    const base = markdownSlug(heading[2]), count = counts.get(base) || 0
    counts.set(base, count + 1)
    anchors.add(count ? `${base}-${count}` : base)
  }
  return anchors
}
