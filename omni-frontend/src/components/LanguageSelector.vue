<script setup lang="ts">
/**
 * 统一语言选择器。
 * 通过可访问的下拉菜单提供中、英、日、韩四种界面语言，并持久化用户偏好。
 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeLang, type SupportedLocale } from '@/i18n'

interface LanguageOption {
  value: SupportedLocale
  labelKey: string
  shortLabel: string
}

const options: LanguageOption[] = [
  { value: 'zh-CN', labelKey: 'lang.zhCN', shortLabel: '中' },
  { value: 'en-US', labelKey: 'lang.enUS', shortLabel: 'EN' },
  { value: 'ja-JP', labelKey: 'lang.jaJP', shortLabel: '日' },
  { value: 'ko-KR', labelKey: 'lang.koKR', shortLabel: '한' },
]

const { t, locale } = useI18n()
const current = computed(() => options.find((option) => option.value === locale.value) || options[0])

/** 应用并持久化用户选择的语言。 */
function selectLanguage(value: SupportedLocale) {
  locale.value = value
  storeLang(value)
}
</script>

<template>
  <el-dropdown trigger="click" @command="selectLanguage">
    <el-button text :title="t('lang.switch')" :aria-label="t('lang.switch')">
      <el-icon><Globe /></el-icon>
      {{ current.shortLabel }}
    </el-button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="option in options"
          :key="option.value"
          :command="option.value"
          :disabled="option.value === locale"
        >
          {{ t(option.labelKey) }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>
