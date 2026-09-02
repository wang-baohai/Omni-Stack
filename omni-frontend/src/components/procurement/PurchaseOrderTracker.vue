<script setup lang="ts">
/** 采购订单进度跟踪共享组件——订单详情与收货进度展示。 */
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ProcurementPurchaseOrderDetail, ProcurementPurchaseOrderStatus } from '@/api/procurement-purchase-order'

const { t } = useI18n()

const statusMap = computed<Record<
  ProcurementPurchaseOrderStatus,
  { label: string; type: 'info' | 'primary' | 'warning' | 'success' | 'danger' }
>>(() => ({
  DRAFT: { label: t('procurementPurchaseOrderTracker.statusDraft'), type: 'info' },
  SENT: { label: t('procurementPurchaseOrderTracker.statusSent'), type: 'primary' },
  CONFIRMED: { label: t('procurementPurchaseOrderTracker.statusConfirmed'), type: 'success' },
  PARTIAL_RECEIVED: { label: t('procurementPurchaseOrderTracker.statusPartialReceived'), type: 'warning' },
  RECEIVED: { label: t('procurementPurchaseOrderTracker.statusReceived'), type: 'success' },
  CLOSED: { label: t('procurementPurchaseOrderTracker.statusClosed'), type: 'info' },
  CANCELLED: { label: t('procurementPurchaseOrderTracker.statusCancelled'), type: 'danger' },
}))

function statusInfo(status: ProcurementPurchaseOrderStatus) {
  return statusMap.value[status]
}

defineProps<{
  orderDetail?: ProcurementPurchaseOrderDetail
}>()
</script>

<template>
  <div v-if="orderDetail" class="order-tracker">
    <el-descriptions :column="3" border>
      <el-descriptions-item :label="t('procurementPurchaseOrderTracker.orderNo')">
        {{ orderDetail.poNo }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('procurementPurchaseOrderTracker.status')">
        <el-tag :type="statusInfo(orderDetail.status).type">
          {{ statusInfo(orderDetail.status).label }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="RFQ ID">{{ orderDetail.rfqId }}</el-descriptions-item>
      <el-descriptions-item :label="t('procurementPurchaseOrderTracker.supplier')">
        {{ orderDetail.supplierNameSnapshot }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('procurementPurchaseOrderTracker.winningQuotation')">
        #{{ orderDetail.quotationId }} / v{{ orderDetail.quotationVersion }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('procurementPurchaseOrderTracker.orderAmount')">
        {{ orderDetail.totalAmount }} {{ orderDetail.currencyCode }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('procurementPurchaseOrderTracker.orderTitle')" :span="2">
        {{ orderDetail.title }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('procurementPurchaseOrderTracker.expectedDelivery')">
        {{ orderDetail.expectedDeliveryDate || '—' }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('procurementPurchaseOrderTracker.deliveryAddress')" :span="2">
        {{ orderDetail.deliveryAddress }}
      </el-descriptions-item>
      <el-descriptions-item :label="t('procurementPurchaseOrderTracker.contact')">
        {{ orderDetail.contactName }} / {{ orderDetail.contactPhone }}
      </el-descriptions-item>
    </el-descriptions>

    <h3>{{ t('procurementPurchaseOrderTracker.linesProgress') }}</h3>
    <el-table :data="orderDetail.lines" border>
      <el-table-column prop="lineNo" :label="t('procurementPurchaseOrderTracker.lineNo')" width="70" />
      <el-table-column prop="materialCode" :label="t('procurementPurchaseOrderTracker.materialCode')" min-width="130" />
      <el-table-column prop="materialName" :label="t('procurementPurchaseOrderTracker.materialName')" min-width="180" />
      <el-table-column prop="quantity" :label="t('procurementPurchaseOrderTracker.orderQuantity')" min-width="110" align="right" />
      <el-table-column prop="receivedQuantity" :label="t('procurementPurchaseOrderTracker.receivedQuantity')" min-width="110" align="right" />
      <el-table-column prop="remainingQuantity" :label="t('procurementPurchaseOrderTracker.remainingQuantity')" min-width="110" align="right" />
      <el-table-column prop="unit" :label="t('procurementPurchaseOrderTracker.unit')" width="80" />
      <el-table-column prop="unitPrice" :label="t('procurementPurchaseOrderTracker.unitPrice')" min-width="120" align="right" />
      <el-table-column prop="totalPrice" :label="t('procurementPurchaseOrderTracker.amount')" min-width="130" align="right" />
      <el-table-column prop="expectedDeliveryDate" :label="t('procurementPurchaseOrderTracker.expectedDelivery')" min-width="120" />
    </el-table>
  </div>
</template>

<style scoped>
h3 {
  margin: 24px 0 12px;
}
</style>
