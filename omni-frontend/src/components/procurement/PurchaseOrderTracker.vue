<script setup lang="ts">
/** 采购订单进度跟踪共享组件——订单详情与收货进度展示。 */
import type { ProcurementPurchaseOrderDetail, ProcurementPurchaseOrderStatus } from '@/api/procurement-purchase-order'

const statusOptions: Array<{
  value: ProcurementPurchaseOrderStatus
  label: string
  type: 'info' | 'primary' | 'warning' | 'success' | 'danger'
}> = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'SENT', label: '已发送', type: 'primary' },
  { value: 'CONFIRMED', label: '已确认', type: 'success' },
  { value: 'PARTIAL_RECEIVED', label: '部分收货', type: 'warning' },
  { value: 'RECEIVED', label: '已收齐', type: 'success' },
  { value: 'CLOSED', label: '已关闭', type: 'info' },
  { value: 'CANCELLED', label: '已取消', type: 'danger' },
]
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<
  ProcurementPurchaseOrderStatus,
  (typeof statusOptions)[number]
>

function statusInfo(status: ProcurementPurchaseOrderStatus) {
  return statusMap[status]
}

defineProps<{
  orderDetail?: ProcurementPurchaseOrderDetail
}>()
</script>

<template>
  <div v-if="orderDetail" class="order-tracker">
    <el-descriptions :column="3" border>
      <el-descriptions-item label="订单号">{{ orderDetail.poNo }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="statusInfo(orderDetail.status).type">
          {{ statusInfo(orderDetail.status).label }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="RFQ ID">{{ orderDetail.rfqId }}</el-descriptions-item>
      <el-descriptions-item label="供应商">{{ orderDetail.supplierNameSnapshot }}</el-descriptions-item>
      <el-descriptions-item label="中标报价">
        #{{ orderDetail.quotationId }} / v{{ orderDetail.quotationVersion }}
      </el-descriptions-item>
      <el-descriptions-item label="订单金额">
        {{ orderDetail.totalAmount }} {{ orderDetail.currencyCode }}
      </el-descriptions-item>
      <el-descriptions-item label="订单标题" :span="2">{{ orderDetail.title }}</el-descriptions-item>
      <el-descriptions-item label="预计交付">
        {{ orderDetail.expectedDeliveryDate || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="收货地址" :span="2">
        {{ orderDetail.deliveryAddress }}
      </el-descriptions-item>
      <el-descriptions-item label="联系人">
        {{ orderDetail.contactName }} / {{ orderDetail.contactPhone }}
      </el-descriptions-item>
    </el-descriptions>

    <h3>订单明细与收货进度</h3>
    <el-table :data="orderDetail.lines" border>
      <el-table-column prop="lineNo" label="行号" width="70" />
      <el-table-column prop="materialCode" label="物料编码" min-width="130" />
      <el-table-column prop="materialName" label="物料名称" min-width="180" />
      <el-table-column prop="quantity" label="订单数量" min-width="110" align="right" />
      <el-table-column prop="receivedQuantity" label="已收数量" min-width="110" align="right" />
      <el-table-column prop="remainingQuantity" label="待收数量" min-width="110" align="right" />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="unitPrice" label="单价" min-width="120" align="right" />
      <el-table-column prop="totalPrice" label="金额" min-width="130" align="right" />
      <el-table-column prop="expectedDeliveryDate" label="预计交付" min-width="120" />
    </el-table>
  </div>
</template>

<style scoped>
h3 {
  margin: 24px 0 12px;
}
</style>
