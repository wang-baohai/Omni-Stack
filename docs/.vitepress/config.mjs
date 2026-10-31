import { readFileSync } from 'node:fs'
import { defineConfig } from 'vitepress'
import { markdownSlug } from '../scripts/markdown-anchors.mjs'
import { buildWikiSidebar } from '../scripts/prepare-site.mjs'

const navigation = JSON.parse(readFileSync(new URL('./navigation.json', import.meta.url), 'utf8'))
const labels = Object.fromEntries(['zh-CN', 'en-US', 'ja-JP', 'ko-KR'].map((locale) =>
  [locale, JSON.parse(readFileSync(new URL(`./locales/${locale}.json`, import.meta.url), 'utf8'))]))
const interfaceText = Object.fromEntries(Object.entries(labels).map(([locale, text]) => [locale, text.interface]))

export default defineConfig({
  title: 'Omni-Stack Wiki',
  srcDir: '.vitepress/source',
  base: process.env.DOCS_BASE || '/',
  cleanUrls: false,
  lastUpdated: false,
  ignoreDeadLinks: false,
  markdown: { anchor: { slugify: markdownSlug } },
  locales: Object.fromEntries(Object.entries(labels).map(([locale, text]) => [locale, {
    label: text.label, lang: locale, link: `/${locale}/`,
    themeConfig: {
      nav: [{ text: text.wiki, link: `/${locale}/wiki/index` }],
      sidebar: buildWikiSidebar(navigation[locale], text),
      outline: { label: text.outline }, sidebarMenuLabel: text.menu, returnToTopLabel: text.back,
      langMenuLabel: interfaceText[locale].language, darkModeSwitchLabel: interfaceText[locale].appearance,
      lightModeSwitchTitle: interfaceText[locale].light, darkModeSwitchTitle: interfaceText[locale].dark,
      skipToContentLabel: interfaceText[locale].skip,
      docFooter: { prev: text.previous, next: text.next },
    },
  }])),
  themeConfig: {
    search: { provider: 'local', options: {
      locales: Object.fromEntries(Object.entries(labels).map(([locale, text]) => [locale, {
        translations: {
          button: { buttonText: text.search, buttonAriaLabel: text.search },
          modal: {
            displayDetails: interfaceText[locale].details, resetButtonTitle: interfaceText[locale].reset,
            backButtonTitle: interfaceText[locale].close, noResultsText: interfaceText[locale].empty,
            footer: {
              selectText: interfaceText[locale].select, selectKeyAriaLabel: interfaceText[locale].enter,
              navigateText: interfaceText[locale].navigate, navigateUpKeyAriaLabel: interfaceText[locale].up,
              navigateDownKeyAriaLabel: interfaceText[locale].down,
              closeText: interfaceText[locale].close, closeKeyAriaLabel: interfaceText[locale].escape,
            },
          },
        },
      }])),
    } },
  },
})
