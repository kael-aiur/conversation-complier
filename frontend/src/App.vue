<script setup>
import { ref } from 'vue'

const apiStatus = ref('未检查')
const checking = ref(false)

async function checkApi() {
  checking.value = true
  try {
    const response = await fetch('/api/health')
    const data = await response.json()
    apiStatus.value = data.status === 'UP' ? '后端已连接' : '后端异常'
  } catch {
    apiStatus.value = '无法连接后端'
  } finally {
    checking.value = false
  }
}
</script>

<template>
  <main class="page-shell">
    <section class="hero-card">
      <p class="eyebrow">SPRING BOOT + VUE 3</p>
      <h1>Conversation Compiler</h1>
      <p class="subtitle">会话编译器项目骨架已就绪。</p>
      <div class="actions">
        <button :disabled="checking" @click="checkApi">
          {{ checking ? '检查中…' : '检查后端连接' }}
        </button>
        <span class="status">{{ apiStatus }}</span>
      </div>
    </section>
  </main>
</template>
