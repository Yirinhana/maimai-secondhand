import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { get, post } from '../api'
import type { LoginRequest, Me, RegisterRequest } from '../types'

const ADMIN_ROLES = ['OPERATOR', 'SUPPORT', 'SUPER_ADMIN']

export const useAuthStore = defineStore('auth', () => {
  const me = ref<Me | null>(null)
  /** 是否已尝试过拉取当前用户（路由守卫避免重复请求） */
  const meLoaded = ref(false)

  const isAdmin = computed(
    () => me.value !== null && me.value.roles.some((r) => ADMIN_ROLES.includes(r)),
  )
  const isSeller = computed(() => me.value?.sellerStatus === 'APPROVED')

  async function fetchMe(): Promise<Me | null> {
    try {
      me.value = await get<Me>('/auth/me')
    } catch {
      me.value = null
    } finally {
      meLoaded.value = true
    }
    return me.value
  }

  async function login(payload: LoginRequest): Promise<Me> {
    me.value = await post<Me>('/auth/login', payload)
    meLoaded.value = true
    return me.value
  }

  async function register(payload: RegisterRequest): Promise<Me> {
    await post<Me>('/auth/register', payload)
    return fetchMe().then((m) => {
      if (!m) throw { code: 'UNKNOWN', message: '注册成功但登录状态获取失败' }
      return m
    })
  }

  async function logout(): Promise<void> {
    await post('/auth/logout')
    me.value = null
    meLoaded.value = true
  }

  return { me, meLoaded, isAdmin, isSeller, fetchMe, login, register, logout }
})
