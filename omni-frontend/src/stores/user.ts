import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const username = ref<string>('')

  function setToken(newToken: string) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUsername(name: string) {
    username.value = name
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('token')
  }

  return { token, username, setToken, setUsername, logout }
})
