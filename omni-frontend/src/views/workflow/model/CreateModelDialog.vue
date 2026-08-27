<script setup lang="ts">
/**
 * 创建模型对话框。
 */
import { computed, ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { createModel, type CreateModelRequest } from '@/api/workflow-model'
import { useDictOptions } from '@/composables/useDictOptions'
import { getErrorMessage } from '@/utils/errors'

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const { options: categoryOptions } = useDictOptions('workflow_category')

const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive<CreateModelRequest>({
  modelKey: '',
  modelName: '',
  category: '',
})

const rules = computed(() => ({
  modelKey: [
    { required: true, message: t('workflow.modelKeyRequired'), trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9-]*$/, message: t('workflow.modelKeyPattern'), trigger: 'blur' },
  ],
  modelName: [
    { required: true, message: t('workflow.modelNameRequired'), trigger: 'blur' },
  ],
  category: [
    { required: true, message: t('workflow.categoryRequired'), trigger: 'change' },
  ],
}))

watch(() => props.visible, (val) => {
  if (val) {
    form.modelKey = ''
    form.modelName = ''
    form.category = ''
    formRef.value?.resetFields()
  }
})

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await createModel(form)
    ElMessage.success(t('workflow.modelCreateSuccess'))
    emit('success')
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, t('workflow.modelCreateFailed')))
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="t('workflow.createModelTitle')"
    width="500px"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="t('workflow.modelKey')" prop="modelKey">
        <el-input v-model="form.modelKey" :placeholder="t('workflow.modelKeyPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('workflow.modelName')" prop="modelName">
        <el-input v-model="form.modelName" :placeholder="t('workflow.modelNamePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('workflow.category')" prop="category">
        <el-select v-model="form.category" :placeholder="t('common.selectPlaceholder')" style="width: 100%">
          <el-option
            v-for="opt in categoryOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>
