<script setup lang="ts">
/** 请购审批规则业务化页面：先试算、持续看风险，再维护规则。 */
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ProcurementApprovalRoute } from '@/api/procurement-approval-route'
import ApprovalRuleWizard from './components/ApprovalRuleWizard.vue'
import RuleAdvancedInfo from './components/RuleAdvancedInfo.vue'
import RuleCoverageAlert from './components/RuleCoverageAlert.vue'
import RuleMatchTester from './components/RuleMatchTester.vue'
import { useApprovalRules } from './composables/useApprovalRules'

const { t } = useI18n()
const {
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
} = useApprovalRules(t)

function amountRange(route: ProcurementApprovalRoute) {
  return `${route.minAmount} ≤ x < ${route.maxAmount || '∞'}`
}

function workflowLabel(route: ProcurementApprovalRoute) {
  if (route.workflowAvailability === 'UNAVAILABLE') {
    return t('procurementApprovalRules.unavailableFlow')
  }
  if (route.workflowAvailability === 'LEGACY_CATEGORY') {
    return t('procurementApprovalRules.legacyFlow')
  }
  if (!route.modelName) return t('procurementApprovalRules.noFlow')
  return route.modelVersion
    ? `${route.modelName} · ${t('procurementApprovalRules.version', { version: route.modelVersion })}`
    : route.modelName
}

function workflowTagType(route: ProcurementApprovalRoute) {
  return route.workflowAvailability === 'AVAILABLE' ? 'success' : 'warning'
}

onMounted(() => initialize())
</script>

<template>
  <main class="approval-rules-page">
    <header class="page-heading">
      <div>
        <h1>{{ t('procurementApprovalRules.title') }}</h1>
        <p>{{ t('procurementApprovalRules.description') }}</p>
      </div>
      <el-button
        v-permission="'procurement:approval-route:create'"
        type="primary"
        @click="openCreate"
      >
        {{ t('procurementApprovalRules.create') }}
      </el-button>
    </header>

    <RuleMatchTester :categories="categoryOptions.filter((item) => item.value !== '*')" />
    <RuleCoverageAlert
      :report="coverage"
      :loading="coverageLoading"
      @refresh="loadCoverage"
    />

    <el-card shadow="never" class="rules-card">
      <el-form :inline="true" :model="query" class="filter-form">
        <el-form-item :label="t('procurementApprovalRules.keyword')">
          <el-input
            v-model="query.keyword"
            clearable
            :placeholder="t('procurementApprovalRules.keywordPlaceholder')"
            @keyup.enter="search"
          />
        </el-form-item>
        <el-form-item :label="t('procurementApprovalRules.category')">
          <el-select
            v-model="query.categoryCode"
            clearable
            filterable
            :placeholder="t('procurementApprovalRules.allCategories')"
          >
            <el-option
              v-for="option in categoryOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('procurementApprovalRules.status')">
          <el-select
            v-model="query.status"
            clearable
            :placeholder="t('procurementApprovalRules.allStatuses')"
          >
            <el-option :label="t('procurementApprovalRules.active')" value="ACTIVE" />
            <el-option :label="t('procurementApprovalRules.inactive')" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">
            {{ t('procurementApprovalRules.search') }}
          </el-button>
          <el-button @click="resetQuery">
            {{ t('procurementApprovalRules.reset') }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="desktop-table">
        <el-table v-loading="loading" :data="rows" stripe>
          <el-table-column prop="routeName" :label="t('procurementApprovalRules.ruleName')" min-width="190" />
          <el-table-column :label="t('procurementApprovalRules.category')" min-width="230">
            <template #default="{ row }">{{ categoryLabel(row.categoryCode) }}</template>
          </el-table-column>
          <el-table-column :label="t('procurementApprovalRules.amountRange')" min-width="190">
            <template #default="{ row }">{{ amountRange(row) }}</template>
          </el-table-column>
          <el-table-column :label="t('procurementApprovalRules.approvalFlow')" min-width="240">
            <template #default="{ row }">
              <el-tag :type="workflowTagType(row)" effect="plain">
                {{ workflowLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('procurementApprovalRules.status')" width="90">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
                {{ row.status === 'ACTIVE'
                  ? t('procurementApprovalRules.active')
                  : t('procurementApprovalRules.inactive') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('procurementApprovalRules.actions')" width="210" fixed="right">
            <template #default="{ row }">
              <el-button
                v-permission="'procurement:approval-route:update'"
                link
                type="primary"
                @click="openEdit(row)"
              >
                {{ t('procurementApprovalRules.edit') }}
              </el-button>
              <el-button
                v-permission="'procurement:approval-route:update'"
                link
                :type="row.status === 'ACTIVE' ? 'warning' : 'success'"
                @click="toggleStatus(row)"
              >
                {{ row.status === 'ACTIVE'
                  ? t('procurementApprovalRules.deactivate')
                  : t('procurementApprovalRules.activate') }}
              </el-button>
              <el-button
                v-permission="'procurement:approval-route:delete'"
                link
                type="danger"
                @click="remove(row)"
              >
                {{ t('procurementApprovalRules.delete') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-loading="loading" class="mobile-cards">
        <el-empty v-if="!rows.length" :description="t('procurementApprovalRules.empty')" />
        <article v-for="route in rows" :key="route.id" class="rule-card">
          <div class="rule-card-heading">
            <strong>{{ route.routeName }}</strong>
            <el-tag :type="route.status === 'ACTIVE' ? 'success' : 'info'">
              {{ route.status === 'ACTIVE'
                ? t('procurementApprovalRules.active')
                : t('procurementApprovalRules.inactive') }}
            </el-tag>
          </div>
          <dl>
            <div><dt>{{ t('procurementApprovalRules.category') }}</dt><dd>{{ categoryLabel(route.categoryCode) }}</dd></div>
            <div><dt>{{ t('procurementApprovalRules.amountRange') }}</dt><dd>{{ amountRange(route) }}</dd></div>
            <div><dt>{{ t('procurementApprovalRules.approvalFlow') }}</dt><dd>{{ workflowLabel(route) }}</dd></div>
          </dl>
          <RuleAdvancedInfo :route="route" />
          <div class="card-actions">
            <el-button
              v-permission="'procurement:approval-route:update'"
              type="primary"
              @click="openEdit(route)"
            >
              {{ t('procurementApprovalRules.edit') }}
            </el-button>
            <el-button
              v-permission="'procurement:approval-route:update'"
              @click="toggleStatus(route)"
            >
              {{ route.status === 'ACTIVE'
                ? t('procurementApprovalRules.deactivate')
                : t('procurementApprovalRules.activate') }}
            </el-button>
            <el-button
              v-permission="'procurement:approval-route:delete'"
              type="danger"
              plain
              @click="remove(route)"
            >
              {{ t('procurementApprovalRules.delete') }}
            </el-button>
          </div>
        </article>
      </div>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[5, 10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="loadRows"
        @size-change="search"
      />
    </el-card>

    <ApprovalRuleWizard
      v-model="dialogVisible"
      :route="editingRoute"
      :categories="categoryOptions"
      :workflows="workflows"
      :workflow-loading="workflowLoading"
      :saving="saving"
      @save="save"
    />
  </main>
</template>

<style scoped>
.approval-rules-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.page-heading {
  display: flex;
  gap: 20px;
  align-items: flex-start;
  justify-content: space-between;
}

.page-heading h1 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 24px;
}

.page-heading p {
  max-width: 760px;
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.filter-form :deep(.el-select) {
  width: 220px;
}

.mobile-cards {
  display: none;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 768px) {
  .page-heading {
    flex-direction: column;
  }

  .page-heading :deep(.el-button) {
    width: 100%;
  }

  .filter-form {
    display: grid;
  }

  .filter-form :deep(.el-form-item),
  .filter-form :deep(.el-input),
  .filter-form :deep(.el-select) {
    width: 100%;
    margin-right: 0;
  }

  .desktop-table {
    display: none;
  }

  .mobile-cards {
    display: grid;
    gap: 12px;
    min-height: 80px;
  }

  .rule-card {
    min-width: 0;
    padding: 14px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 10px;
  }

  .rule-card-heading {
    display: flex;
    gap: 12px;
    align-items: flex-start;
    justify-content: space-between;
  }

  .rule-card dl {
    display: grid;
    gap: 10px;
    margin: 14px 0 0;
  }

  .rule-card dl div {
    display: grid;
    gap: 3px;
  }

  .rule-card dt {
    color: var(--el-text-color-secondary);
    font-size: 12px;
  }

  .rule-card dd {
    min-width: 0;
    margin: 0;
    overflow-wrap: anywhere;
  }

  .card-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px;
    margin-top: 12px;
  }

  .card-actions :deep(.el-button) {
    width: 100%;
    margin-left: 0;
  }

  .pagination {
    justify-content: center;
    overflow-x: auto;
  }
}

@media (min-width: 769px) {
  .desktop-table {
    display: block;
  }
}
</style>
