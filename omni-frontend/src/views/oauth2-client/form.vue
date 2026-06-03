<script setup lang="ts">
/**
 * OAuth2 客户端创建/编辑表单弹窗。
 */
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createOAuth2Client,
  updateOAuth2Client,
} from '@/api/oauth2-client'
import type { OAuth2ClientVO } from '@/api/oauth2-client'

const { t } = useI18n()

const props = defineProps<{
  /** 弹窗是否可见，支持 v-model:visible 双向绑定 */
  visible: boolean
  /** 编辑的客户端对象，null 表示新建模式 */
  client: OAuth2ClientVO | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
}>()

const formRef = ref<FormInstance>()
const loading = ref(false)

/** 表单数据 */
const form = reactive({
  clientName: '',
  clientId: '',
  clientSecret: '',
  authenticationMethods: [] as string[],
  grantTypes: [] as string[],
  redirectUris: '' as string,
  postLogoutRedirectUris: '' as string,
  scopes: [] as string[],
  requireConsent: false,
  requireProofKey: true,
})

/** 是否是编辑模式 */
const isEdit = computed(() => !!props.client)

/** 表单校验规则 */
const rules: FormRules = {
  clientName: [{ required: true, message: '请输入客户端名称', trigger: 'blur' }],
  authenticationMethods: [{ required: true, message: '请选择认证方式', trigger: 'change' }],
  grantTypes: [{ required: true, message: '请选择授权类型', trigger: 'change' }],
  scopes: [{ required: true, message: '请选择作用域', trigger: 'change' }],
}

/** 认证方式选项 */
const authMethodOptions = [
  { label: 'none (PKCE 公有客户端)', value: 'none' },
  { label: 'client_secret_basic', value: 'client_secret_basic' },
  { label: 'client_secret_post', value: 'client_secret_post' },
]

/** 授权类型选项 */
const grantTypeOptions = [
  { label: 'authorization_code', value: 'authorization_code' },
  { label: 'refresh_token', value: 'refresh_token' },
  { label: 'client_credentials', value: 'client_credentials' },
]

/** 作用域选项 */
const scopeOptions = [
  { label: 'openid', value: 'openid' },
  { label: 'profile', value: 'profile' },
  { label: 'email', value: 'email' },
]

/**
 * 监听 visible 变化，编辑模式时填充表单数据。
 */
watch(
  () => props.visible,
  (visible) => {
    if (visible && props.client) {
      form.clientName = props.client.clientName
      form.clientId = props.client.clientId
      form.clientSecret = ''
      form.authenticationMethods = [...props.client.authenticationMethods]
      form.grantTypes = [...props.client.grantTypes]
      form.redirectUris = props.client.redirectUris?.join('\n') || ''
      form.postLogoutRedirectUris = props.client.postLogoutRedirectUris?.join('\n') || ''
      form.scopes = [...props.client.scopes]
      form.requireConsent = props.client.requireConsent
      form.requireProofKey = props.client.requireProofKey
    } else if (visible) {
      // 新建模式：重置表单
      form.clientName = ''
      form.clientId = ''
      form.clientSecret = ''
      form.authenticationMethods = ['none']
      form.grantTypes = ['authorization_code', 'refresh_token']
      form.redirectUris = 'http://localhost:3000/callback'
      form.postLogoutRedirectUris = ''
      form.scopes = ['openid', 'profile']
      form.requireConsent = false
      form.requireProofKey = true
    }
  },
)

/**
 * 提交表单。
 */
async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const redirectUris = form.redirectUris
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)
    const postLogoutRedirectUris = form.postLogoutRedirectUris
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)

    if (isEdit.value && props.client) {
      await updateOAuth2Client(props.client.id, {
        clientName: form.clientName,
        authenticationMethods: form.authenticationMethods,
        grantTypes: form.grantTypes,
        redirectUris,
        postLogoutRedirectUris,
        scopes: form.scopes,
        requireConsent: form.requireConsent,
        requireProofKey: form.requireProofKey,
      })
      ElMessage.success('更新成功')
    } else {
      await createOAuth2Client({
        clientName: form.clientName,
        clientId: form.clientId || undefined,
        clientSecret: form.clientSecret || undefined,
        authenticationMethods: form.authenticationMethods,
        grantTypes: form.grantTypes,
        redirectUris,
        postLogoutRedirectUris,
        scopes: form.scopes,
        requireConsent: form.requireConsent,
        requireProofKey: form.requireProofKey,
      })
      ElMessage.success('创建成功')
    }
    emit('success')
  } catch {
    // 错误已由拦截器处理
  } finally {
    loading.value = false
  }
}

/**
 * 关闭弹窗。
 */
function handleClose() {
  emit('update:visible', false)
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="isEdit ? '编辑 OAuth2 客户端' : '创建 OAuth2 客户端'"
    width="600px"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      label-position="top"
    >
      <el-form-item label="客户端名称" prop="clientName">
        <el-input v-model="form.clientName" placeholder="输入客户端名称" />
      </el-form-item>

      <el-form-item v-if="!isEdit" label="Client ID">
        <el-input
          v-model="form.clientId"
          placeholder="留空则自动生成"
        />
      </el-form-item>

      <el-form-item v-if="!isEdit" label="Client Secret">
        <el-input
          v-model="form.clientSecret"
          type="password"
          show-password
          placeholder="留空则不设置（PKCE 客户端可跳过）"
        />
      </el-form-item>

      <el-form-item label="认证方式" prop="authenticationMethods">
        <el-checkbox-group v-model="form.authenticationMethods">
          <el-checkbox
            v-for="opt in authMethodOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="授权类型" prop="grantTypes">
        <el-checkbox-group v-model="form.grantTypes">
          <el-checkbox
            v-for="opt in grantTypeOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="回调地址（每行一个）">
        <el-input
          v-model="form.redirectUris"
          type="textarea"
          :rows="3"
          placeholder="http://localhost:3000/callback"
        />
      </el-form-item>

      <el-form-item label="登出回调地址（每行一个）">
        <el-input
          v-model="form.postLogoutRedirectUris"
          type="textarea"
          :rows="2"
          placeholder="可选"
        />
      </el-form-item>

      <el-form-item label="作用域" prop="scopes">
        <el-checkbox-group v-model="form.scopes">
          <el-checkbox
            v-for="opt in scopeOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <el-form-item label="客户端设置">
        <el-switch
          v-model="form.requireProofKey"
          active-text="要求 PKCE"
          inactive-text=""
          style="margin-right: 20px"
        />
        <el-switch
          v-model="form.requireConsent"
          active-text="要求授权确认"
          inactive-text=""
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ t('common.confirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>
