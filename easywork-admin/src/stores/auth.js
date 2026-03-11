import { defineStore } from 'pinia'
import { login as loginApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userId: localStorage.getItem('userId') || '',
    employeeNumber: localStorage.getItem('employeeNumber') || '',
    realName: localStorage.getItem('realName') || '',
    role: localStorage.getItem('role') || '',
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.role === 'ADMIN',
  },

  actions: {
    async login(credentials) {
      const data = await loginApi(credentials)
      this.token = data.token
      this.userId = String(data.userId)
      this.employeeNumber = data.employeeNumber
      this.realName = data.realName
      this.role = data.role

      localStorage.setItem('token', data.token)
      localStorage.setItem('userId', data.userId)
      localStorage.setItem('employeeNumber', data.employeeNumber)
      localStorage.setItem('realName', data.realName)
      localStorage.setItem('role', data.role)
    },

    logout() {
      this.token = ''
      this.userId = ''
      this.employeeNumber = ''
      this.realName = ''
      this.role = ''

      localStorage.removeItem('token')
      localStorage.removeItem('userId')
      localStorage.removeItem('employeeNumber')
      localStorage.removeItem('realName')
      localStorage.removeItem('role')

      window.location.href = '/login'
    },
  },
})
