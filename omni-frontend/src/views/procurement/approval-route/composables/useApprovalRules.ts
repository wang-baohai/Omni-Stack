/**
 * 请购审批规则页面状态与命令，所有匹配和覆盖计算均由服务端完成。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ComposerTranslation } from 'vue-i18n'
import {
  createProcurementApprovalRoute,
  deleteProcurementApprovalRoute,
  getApprovalRouteCoverage,
  getApprovalRouteImpact,
  listApprovalWorkflowOptions,
  listProcurementApprovalRoutes,
  updateProcurementApprovalRoute,
  type ApprovalWorkflowOption,
  type CoverageReport,
  type CreateProcurementApprovalRouteRequest,
  type ProcurementApprovalRoute,
  type ProcurementApprovalRouteStatus,
  type UpdateProcurementApprovalRouteRequest,
} from '@/api/procurement-approval-route'
import {
  listProcurementCategories,
  type ProcurementMaterialCategory,
} from '@/api/procurement-material'

export function useApprovalRules(t: ComposerTranslation) {
  const loading = ref(false)
  const coverageLoading = ref(false)
  const workflowLoading = ref(false)
  const saving = ref(false)
  const rows = ref<ProcurementApprovalRoute[]>([])
  const total = ref(0)
  const currentPage = ref(1)
  const pageSize = ref(10)
  const categories = ref<ProcurementMaterialCategory[]>([])
  const workflows = ref<ApprovalWorkflowOption[]>([])
  const coverage = ref<CoverageReport>()
  const dialogVisible = ref(false)
  const editingRoute = ref<ProcurementApprovalRoute>()
  const query = reactive<{
    keyword: string
    categoryCode: string
    status?: ProcurementApprovalRouteStatus
  }>({ keyword: '', categoryCode: '' })

  const categoryOptions = computed(() => {
    const options = [{
      value: '*',
      label: t('procurementApprovalRules.defaultCategory'),
    }]
    function append(items: ProcurementMaterialCategory[], parents: string[] = []) {
      for (const item of items) {
        const path = [...parents, item.categoryName]
        options.push({
          value: item.categoryCode,
          label: `${path.join(' / ')}（${item.categoryCode}）`,
        })
        append(item.children ?? [], path)
      }
    }
    append(categories.value)
    return options
  })

  async function loadRows() {
    loading.value = true
    try {
      const response = await listProcurementApprovalRoutes({
        keyword: query.keyword || undefined,
        categoryCode: query.categoryCode || undefined,
        status: query.status,
        page: currentPage.value,
        size: pageSize.value,
      })
      rows.value = response.data.data.records
      total.value = response.data.data.total
    } finally {
      loading.value = false
    }
  }

  async function loadCoverage() {
    coverageLoading.value = true
    try {
      const response = await getApprovalRouteCoverage()
      coverage.value = response.data.data
    } finally {
      coverageLoading.value = false
    }
  }

  async function loadOptions() {
    const categoryResponse = await listProcurementCategories()
    categories.value = categoryResponse.data.data
    workflowLoading.value = true
    try {
      const workflowResponse = await listApprovalWorkflowOptions()
      workflows.value = workflowResponse.data.data
    } catch {
      workflows.value = []
    } finally {
      workflowLoading.value = false
    }
  }

  async function initialize() {
    await Promise.all([loadRows(), loadCoverage(), loadOptions()])
  }

  function search() {
    currentPage.value = 1
    return loadRows()
  }

  function resetQuery() {
    Object.assign(query, { keyword: '', categoryCode: '', status: undefined })
    return search()
  }

  function openCreate() {
    editingRoute.value = undefined
    dialogVisible.value = true
  }

  function openEdit(route: ProcurementApprovalRoute) {
    editingRoute.value = route
    dialogVisible.value = true
  }

  async function save(
    payload: CreateProcurementApprovalRouteRequest | UpdateProcurementApprovalRouteRequest,
  ) {
    saving.value = true
    try {
      if (editingRoute.value && 'version' in payload) {
        await updateProcurementApprovalRoute(editingRoute.value.id, payload)
      } else {
        await createProcurementApprovalRoute(payload)
      }
      ElMessage.success(t('procurementApprovalRules.saveSuccess'))
      dialogVisible.value = false
      await Promise.all([loadRows(), loadCoverage()])
    } finally {
      saving.value = false
    }
  }

  async function confirmImpact(route: ProcurementApprovalRoute, actionMessage: string) {
    const impactResponse = await getApprovalRouteImpact(route.id)
    try {
      await ElMessageBox.confirm(
        `${actionMessage}\n\n${impactResponse.data.data.actionMessage}`,
        t('procurementApprovalRules.impactTitle'),
        { type: impactResponse.data.data.gapSegmentCount > 0 ? 'warning' : 'info' },
      )
      return true
    } catch {
      return false
    }
  }

  async function remove(route: ProcurementApprovalRoute) {
    const confirmed = await confirmImpact(
      route,
      t('procurementApprovalRules.deleteConfirm', { name: route.routeName }),
    )
    if (!confirmed) return
    await deleteProcurementApprovalRoute(route.id, route.version)
    ElMessage.success(t('procurementApprovalRules.deleteSuccess'))
    await Promise.all([loadRows(), loadCoverage()])
  }

  async function toggleStatus(route: ProcurementApprovalRoute) {
    const nextStatus: ProcurementApprovalRouteStatus = route.status === 'ACTIVE'
      ? 'INACTIVE'
      : 'ACTIVE'
    if (nextStatus === 'INACTIVE') {
      const confirmed = await confirmImpact(
        route,
        t('procurementApprovalRules.deactivateConfirm', { name: route.routeName }),
      )
      if (!confirmed) return
    }
    await updateProcurementApprovalRoute(route.id, {
      routeName: route.routeName,
      categoryCode: route.categoryCode,
      minAmount: route.minAmount,
      maxAmount: route.maxAmount ?? undefined,
      modelVersionId: route.modelVersionId,
      status: nextStatus,
      version: route.version,
    })
    await Promise.all([loadRows(), loadCoverage()])
  }

  function categoryLabel(code: string) {
    return categoryOptions.value.find((option) => option.value === code)?.label ?? code
  }

  return {
    categoryLabel,
    categoryOptions,
    coverage,
    coverageLoading,
    currentPage,
    dialogVisible,
    editingRoute,
    initialize,
    loadCoverage,
    loadRows,
    loading,
    openCreate,
    openEdit,
    pageSize,
    query,
    remove,
    resetQuery,
    rows,
    save,
    saving,
    search,
    toggleStatus,
    total,
    workflowLoading,
    workflows,
  }
}
