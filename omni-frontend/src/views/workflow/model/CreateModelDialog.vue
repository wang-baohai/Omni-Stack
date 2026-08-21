<script setup lang="ts">
/**
 * 创建模型对话框。
 */
import { ref, reactive, watch } from 'vue'
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

const rules = {
  modelKey: [
    { required: true, message: '请输入模型标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9-]*$/, message: '必须以字母开头，仅含字母、数字、短横线', trigger: 'blur' },
  ],
  modelName: [
    { required: true, message: '请输入模型名称', trigger: 'blur' },
  ],
  category: [
    { required: true, message: '请选择流程分类', trigger: 'change' },
  ],
}

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
    ElMessage.success('模型创建成功')
    emit('success')
  } catch (error: unknown) {
    ElMessage.error(getErrorMessage(error, '创建失败'))
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
    title="新建流程模型"
    width="500px"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="模型标识" prop="modelKey">
        <el-input v-model="form.modelKey" placeholder="如 leave-approval，创建后不可修改" />
      </el-form-item>
      <el-form-item label="模型名称" prop="modelName">
        <el-input v-model="form.modelName" placeholder="如 请假审批" />
      </el-form-item>
      <el-form-item label="流程分类" prop="category">
        <el-select v-model="form.category" placeholder="请选择" style="width: 100%">
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
