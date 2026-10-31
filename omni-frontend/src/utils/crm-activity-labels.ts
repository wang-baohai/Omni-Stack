/** CRM 活动的默认字典标签随界面语言显示，租户自定义文案保持原样。 */
import type { DictOption } from '@/api/dict'
import zhCN from '../locales/zh-CN.ts'

type ActivityDictionary = 'crm_activity_type' | 'crm_activity_status' | 'crm_root_type'

const defaults: Record<ActivityDictionary, { group: string; labels: Record<string, string> }> = {
  crm_activity_type: {
    group: 'activityTypes',
    labels: zhCN.crmActivity.activityTypes,
  },
  crm_activity_status: {
    group: 'statuses',
    labels: zhCN.crmActivity.statuses,
  },
  crm_root_type: {
    group: 'rootTypes',
    labels: zhCN.crmActivity.rootTypes,
  },
}

/**
 * 只映射仍使用正式初始中文标签的已知选项，不改变选项值、顺序或缓存。
 */
export function localizeCrmActivityOptions(
  options: readonly DictOption[],
  dictionary: ActivityDictionary,
  translate: (key: string) => string,
): DictOption[] {
  const definition = defaults[dictionary]
  return options.map((option) => {
    const defaultLabel = definition.labels[option.value]
    if (!defaultLabel || option.label !== defaultLabel) return option
    return { ...option, label: translate(`crmActivity.${definition.group}.${option.value}`) }
  })
}
