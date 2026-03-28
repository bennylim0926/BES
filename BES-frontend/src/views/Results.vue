<script setup>
import { ref } from 'vue'
import { getResultsByRefCode } from '@/utils/api'

const refCode = ref('')
const loading = ref(false)
const results = ref(null)
const errorMsg = ref('')

const formatRefCode = (val) => {
  const clean = val.toUpperCase().replace(/[^A-Z0-9]/g, '')
  if (clean.length <= 4) return clean
  return clean.slice(0, 4) + '-' + clean.slice(4, 8)
}

const onInput = (e) => {
  refCode.value = formatRefCode(e.target.value)
}

const lookup = async () => {
  const code = refCode.value.trim()
  if (code.length !== 9) {
    errorMsg.value = 'Please enter a valid reference code (e.g. AB3K-9XPQ)'
    return
  }
  loading.value = true
  errorMsg.value = ''
  results.value = null

  const data = await getResultsByRefCode(code)
  loading.value = false

  if (data?.error) {
    errorMsg.value = data.error
  } else {
    results.value = data
  }
}

const isMultiCriteria = (scores) => scores?.some(s => s.aspect && s.aspect !== '')

const totalScore = (scores) => {
  if (!scores || scores.length === 0) return '—'
  if (isMultiCriteria(scores)) {
    const byJudge = groupScoresByJudge(scores)
    const total = Object.values(byJudge).reduce((acc, aspects) => {
      const vals = Object.values(aspects)
      return acc + vals.reduce((s, v) => s + v, 0) / vals.length
    }, 0)
    return Number(total.toFixed(1))
  }
  const sum = scores.reduce((acc, s) => acc + (s.score ?? 0), 0)
  return Number(sum.toFixed(1))
}

const groupScoresByJudge = (scores) => {
  const byJudge = {}
  for (const s of scores) {
    if (!byJudge[s.judgeName]) byJudge[s.judgeName] = {}
    byJudge[s.judgeName][s.aspect || 'Score'] = s.score
  }
  return byJudge
}

const groupTags = (tags) => {
  const groups = {}
  for (const tag of tags) {
    if (!groups[tag.groupName]) groups[tag.groupName] = []
    groups[tag.groupName].push(tag)
  }
  return groups
}
</script>

<template>
  <div class="min-h-screen bg-surface-900 flex flex-col">
    <!-- Header -->
    <header class="border-b border-surface-700/50 bg-surface-900/80 backdrop-blur-sm">
      <div class="max-w-5xl mx-auto px-4 py-4 flex items-center gap-3">
        <div class="w-8 h-8 rounded-lg bg-primary-600 flex items-center justify-center flex-shrink-0">
          <i class="pi pi-star text-white text-sm"></i>
        </div>
        <div>
          <p class="font-heading font-bold text-content-primary text-sm leading-none">BES Results Portal</p>
          <p class="text-xs text-content-muted mt-0.5">Battle Event System</p>
        </div>
      </div>
    </header>

    <main class="flex-1 max-w-5xl w-full mx-auto px-4 py-10">

      <!-- Lookup card -->
      <div class="card p-6 mb-8 max-w-xl mx-auto">
        <h1 class="font-heading font-bold text-content-primary text-xl mb-1">View Your Results</h1>
        <p class="text-sm text-content-muted mb-5">Enter the reference code from your registration email to view your scores and judge feedback.</p>

        <div class="flex gap-3">
          <div class="flex-1">
            <input
              :value="refCode"
              @input="onInput"
              placeholder="e.g. AB3K-9XPQ"
              maxlength="9"
              class="input-base font-source text-lg tracking-widest uppercase w-full"
              @keydown.enter="lookup"
            />
          </div>
          <button
            @click="lookup"
            :disabled="loading || refCode.length !== 9"
            class="px-5 py-2.5 rounded-xl bg-primary-600 text-white font-semibold text-sm
                   hover:bg-primary-500 active:bg-primary-700 disabled:opacity-40 disabled:cursor-not-allowed
                   transition-all duration-200 flex items-center gap-2 flex-shrink-0"
          >
            <i v-if="loading" class="pi pi-spin pi-spinner"></i>
            <i v-else class="pi pi-search"></i>
            {{ loading ? 'Looking up…' : 'Look up' }}
          </button>
        </div>

        <div
          v-if="errorMsg"
          class="mt-4 flex items-start gap-2.5 px-4 py-3 rounded-xl border border-red-500/30 bg-red-500/8"
        >
          <i class="pi pi-exclamation-circle text-red-400 mt-0.5 flex-shrink-0"></i>
          <p class="text-sm text-red-300">{{ errorMsg }}</p>
        </div>
      </div>

      <!-- Results -->
      <template v-if="results">
        <!-- Participant header -->
        <div class="flex items-center gap-4 mb-6 max-w-xl mx-auto">
          <div class="w-12 h-12 rounded-2xl bg-primary-600/20 border border-primary-500/30 flex items-center justify-center flex-shrink-0">
            <i class="pi pi-user text-primary-400 text-lg"></i>
          </div>
          <div>
            <h2 class="font-heading font-bold text-content-primary text-xl">{{ results.participantName }}</h2>
            <p class="text-sm text-content-muted">{{ results.eventName }}</p>
          </div>
        </div>

        <!-- Genre results — row layout, centered when single -->
        <div
          :class="results.genres.length === 1
            ? 'flex justify-center'
            : 'grid grid-cols-1 sm:grid-cols-2 gap-4'"
        >
          <div
            v-for="genre in results.genres"
            :key="genre.genreName"
            class="card p-5"
            :class="results.genres.length === 1 ? 'w-full max-w-xl' : ''"
          >
            <!-- Genre header -->
            <div class="flex items-center justify-between mb-4">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="px-3 py-1 rounded-full bg-primary-500/15 text-primary-400 text-xs font-bold border border-primary-500/25 uppercase tracking-wide">
                  {{ genre.genreName }}
                </span>
                <span
                  v-if="genre.format"
                  class="px-2 py-0.5 rounded-full bg-surface-700 text-content-muted text-xs font-source border border-surface-600/60"
                >
                  {{ genre.format }}
                </span>
                <span v-if="genre.auditionNumber" class="text-xs text-content-muted">
                  Audition #{{ genre.auditionNumber }}
                </span>
              </div>
              <div v-if="genre.scores && genre.scores.length > 0" class="text-right ml-2 flex-shrink-0">
                <p class="text-xs text-content-muted">Total</p>
                <p class="font-source font-bold text-primary-400 text-xl">{{ totalScore(genre.scores) }}</p>
              </div>
            </div>

            <!-- Scores -->
            <div v-if="genre.scores && genre.scores.length > 0" class="mb-4">
              <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-2">Scores</p>

              <template v-if="isMultiCriteria(genre.scores)">
                <div class="space-y-3">
                  <div
                    v-for="(aspects, judge) in groupScoresByJudge(genre.scores)"
                    :key="judge"
                    class="rounded-xl bg-surface-700/50 border border-surface-600/40 px-3 py-2.5"
                  >
                    <p class="text-xs font-semibold text-content-muted mb-2">{{ judge }}</p>
                    <div class="space-y-1">
                      <div
                        v-for="(score, aspect) in aspects"
                        :key="aspect"
                        class="flex items-center justify-between"
                      >
                        <span class="text-xs text-content-secondary">{{ aspect }}</span>
                        <span class="font-source font-bold text-primary-400 text-sm">{{ score }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              </template>

              <template v-else>
                <div class="grid grid-cols-2 gap-2">
                  <div
                    v-for="score in genre.scores"
                    :key="score.judgeName"
                    class="rounded-xl bg-surface-700/50 border border-surface-600/40 px-3 py-2.5"
                  >
                    <p class="text-xs text-content-muted truncate">{{ score.judgeName }}</p>
                    <p class="font-source font-bold text-content-primary text-lg">{{ score.score }}</p>
                  </div>
                </div>
              </template>
            </div>
            <div v-else class="mb-4">
              <p class="text-sm text-content-muted italic">No scores recorded yet</p>
            </div>

            <!-- Feedback -->
            <template v-if="genre.feedback && genre.feedback.length > 0">
              <div class="border-t border-surface-700/50 pt-4">
                <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-3">Judge Feedback</p>
                <div class="space-y-4">
                  <div
                    v-for="entry in genre.feedback"
                    :key="entry.judgeName"
                    class="rounded-xl border border-surface-600/40 bg-surface-700/20 p-3.5"
                  >
                    <p class="text-xs font-bold text-content-secondary mb-2">{{ entry.judgeName }}</p>

                    <template v-if="entry.tags && entry.tags.length > 0">
                      <div
                        v-for="(tags, groupName) in groupTags(entry.tags)"
                        :key="groupName"
                        class="mb-2"
                      >
                        <p class="text-xs text-content-muted mb-1">{{ groupName }}</p>
                        <div class="flex flex-wrap gap-1.5">
                          <span
                            v-for="tag in tags"
                            :key="tag.label"
                            class="text-xs px-2.5 py-1 rounded-full font-medium border"
                            :class="groupName === 'Strengths'
                              ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                              : 'bg-amber-500/10 text-amber-400 border-amber-500/30'"
                          >{{ tag.label }}</span>
                        </div>
                      </div>
                    </template>

                    <div v-if="entry.note" class="mt-2 pt-2 border-t border-surface-600/30">
                      <p class="text-xs text-content-secondary italic">"{{ entry.note }}"</p>
                    </div>

                    <p
                      v-if="(!entry.tags || entry.tags.length === 0) && !entry.note"
                      class="text-xs text-content-muted italic"
                    >No feedback provided</p>
                  </div>
                </div>
              </div>
            </template>
            <div v-else class="border-t border-surface-700/50 pt-4">
              <p class="text-xs text-content-muted italic">No judge feedback for this genre</p>
            </div>
          </div>
        </div>
      </template>

    </main>

    <footer class="border-t border-surface-700/50 py-4">
      <p class="text-center text-xs text-content-muted">Battle Event System · Results Portal</p>
    </footer>
  </div>
</template>
