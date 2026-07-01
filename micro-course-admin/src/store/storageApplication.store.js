import { defineStore } from 'pinia'
import {
  initStorageDraft, getStorageDetail, saveStorageApplication,
  autoSaveStorageApplication, submitStorageApplication,
  resetStorageModule, resetStorageAll,
  getMyStorageDrafts, uploadStorageImage
} from '@/api/storageApplication'

export const useStorageApplicationStore = defineStore('storageApplication', {
  state: () => ({
    draftId: null,
    currentProposal: null,
    myDrafts: [],
    saving: false,
    lastSavedAt: null,
    dirty: false,
    loading: false
  }),

  actions: {
    async initDraft() {
      const res = await initStorageDraft()
      this.draftId = res.data
      return this.draftId
    },

    async fetchDetail(id) {
      this.loading = true
      try {
        const res = await getStorageDetail(id)
        this.currentProposal = res.data
        this.draftId = id
      } finally {
        this.loading = false
      }
    },

    async fetchMyDrafts() {
      const res = await getMyStorageDrafts()
      this.myDrafts = res.data || []
      return this.myDrafts
    },

    async save(formData) {
      this.saving = true
      try {
        const res = await saveStorageApplication(this.draftId, formData)
        this.currentProposal = res.data
        this.lastSavedAt = new Date()
        this.dirty = false
        return true
      } finally {
        this.saving = false
      }
    },

    async autoSave(formData) {
      if (!this.draftId || !this.dirty) return
      try {
        await autoSaveStorageApplication(this.draftId, formData)
        this.lastSavedAt = new Date()
        this.dirty = false
      } catch { /* auto-save errors are silent */ }
    },

    async uploadImage(file, type) {
      return await uploadStorageImage(this.draftId, file, type)
    },

    async submit() {
      await submitStorageApplication(this.draftId)
    },

    async resetModule(module) {
      await resetStorageModule(this.draftId, module)
    },

    async resetAll() {
      await resetStorageAll(this.draftId)
    },

    markDirty() {
      this.dirty = true
    }
  }
})
