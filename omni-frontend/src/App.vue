<script setup lang="ts">
/**
 * 应用根组件。
 * 作为 Vue Router 的顶层出口，所有页面组件通过 <router-view /> 渲染。
 * 同时向 Element Plus 提供与 vue-i18n 一致的四语言区域配置。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import zhCN from 'element-plus/es/locale/lang/zh-cn'
import enUS from 'element-plus/es/locale/lang/en'
import jaJP from 'element-plus/es/locale/lang/ja'
import koKR from 'element-plus/es/locale/lang/ko'
import type { SupportedLocale } from '@/i18n'

const { locale } = useI18n()
const elementLocales = { 'zh-CN': zhCN, 'en-US': enUS, 'ja-JP': jaJP, 'ko-KR': koKR }
const elementLocale = computed(() => elementLocales[locale.value as SupportedLocale] || enUS)
</script>

<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>
