import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'

export interface UserInfo {
  id: number
  username: string
  email: string
}

export function getUserById(id: number) {
  return request.get<ApiResponse<UserInfo>>(`/business/user/${id}`)
}

export function listUsers(page: number, size: number) {
  return request.get<ApiResponse<PageResult<UserInfo>>>(
    `/business/user/list?page=${page}&size=${size}`,
  )
}

export function createUser(data: { username: string; email: string }) {
  return request.post<ApiResponse<void>>('/business/user', data)
}
