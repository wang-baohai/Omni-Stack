/**
 * @module api
 * 前后端共享类型定义（唯一来源）。
 * 对应后端 `R<T>` 和 `PageResult<T>` 封装，禁止在其他文件中重复定义。
 */

/**
 * 统一 API 响应类型定义，对应后端的 R<T> 封装。
 *
 * @template T 响应数据的泛型类型
 */
export interface ApiResponse<T = unknown> {
  /** 响应状态码，200 表示成功 */
  code: number
  /** 响应消息 */
  message: string
  /** 响应数据体 */
  data: T
}

/**
 * 分页查询结果类型定义，对应后端的 PageResult<T>。
 *
 * @template T 记录列表中元素的泛型类型
 */
export interface PageResult<T> {
  /** 当前页记录列表 */
  records: T[]
  /** 总记录数 */
  total: number
  /** 每页大小 */
  size: number
  /** 当前页码 */
  current: number
  /** 总页数 */
  pages: number
}
