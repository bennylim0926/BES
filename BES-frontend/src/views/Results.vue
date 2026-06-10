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
  <div class="min-h-screen flex overflow-hidden bg-surface-950 relative">

    <!-- Color bleed -->
    <div class="color-bleed"></div>

    <div class="flex-1 flex flex-col lg:flex-row relative z-10">

      <!-- ── Left panel: BES hero (≥md) ────────────────────────────── -->
      <div class="hidden lg:flex lg:w-[44%] relative flex-col justify-between p-14 overflow-hidden">
        <div class="absolute inset-0 bg-surface-900"></div>
        <div class="corner-bar-tl" style="height: 40%"></div>
        <div class="corner-bar-bl" style="width: 40%"></div>

        <!-- Wordmark -->
        <div class="relative z-10 flex items-center gap-2.5">
          <div class="glow-dot"></div>
          <span class="type-body tracking-[0.12em]">BES</span>
        </div>

        <!-- Hero -->
        <div class="relative z-10">
          <div class="type-display mb-6">BES</div>
          <div class="section-rule mb-6">
            <div class="section-rule-line"></div>
          </div>
          <p class="type-body text-content-secondary leading-relaxed max-w-md">
            View your scores, judge feedback, and performance details — all in one place.
          </p>
          <div class="flex flex-wrap gap-2 mt-8">
            <span class="badge-neutral px-3 py-1.5">Scores</span>
            <span class="badge-neutral px-3 py-1.5">Feedback</span>
            <span class="badge-neutral px-3 py-1.5">Results</span>
          </div>
        </div>

        <!-- Footer -->
        <div class="relative z-10 type-label text-content-muted">
          &copy; {{ new Date().getFullYear() }} BES Platform
        </div>
      </div>

      <!-- ── Right panel: Results lookup ───────────────────────── -->
      <div class="flex-1 flex flex-col items-center justify-center p-6 sm:p-8 lg:p-14 overflow-y-auto">
        <div class="w-full max-w-xl">

          <!-- Lookup form -->
          <div class="relative" v-if="!results">
            <div class="corner-bar-tl"></div>
            <div class="corner-bar-bl"></div>
            <div class="p-8 bg-surface-900 border border-[rgba(255,255,255,0.07)]"
              style="clip-path: polygon(8px 0%,100% 0%,calc(100% - 8px) 100%,0% 100%)">

              <div class="mb-6">
                <!-- h1 for document outline; label is now a real <label> tied to the input -->
                <h1 class="type-page-title mb-1">My Results</h1>
                <label for="ref-code" class="type-label text-content-muted">Enter your reference code</label>
              </div>

              <div class="flex gap-3">
                <div class="flex-1">
                  <input
                    id="ref-code"
                    :value="refCode"
                    @input="onInput"
                    placeholder="e.g. AB3K-9XPQ"
                    maxlength="9"
                    autocomplete="off"
                    autocapitalize="characters"
                    class="input-base w-full"
                    :aria-invalid="errorMsg ? 'true' : undefined"
                    aria-describedby="ref-code-error"
                    @keydown.enter="lookup"
                  />
                </div>
                <!-- specific action verb + aria-busy loading state -->
                <button
                  @click="lookup"
                  :disabled="loading || refCode.length !== 9"
                  :aria-busy="loading"
                  class="px-5 py-2.5 min-h-[44px] bg-accent text-surface-900 type-body transition-all duration-200 disabled:opacity-40 disabled:cursor-not-allowed flex items-center gap-2 flex-shrink-0"
                  style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)"
                >
                  <i v-if="loading" class="pi pi-spin pi-spinner" aria-hidden="true"></i>
                  <i v-else class="pi pi-search" aria-hidden="true"></i>
                  {{ loading ? 'Looking up…' : 'Look up results' }}
                </button>
              </div>

              <!-- Error state — role=alert announces lookup failures -->
              <div
                v-if="errorMsg"
                id="ref-code-error"
                role="alert"
                class="mt-4 semantic-chip-error flex items-start gap-3 p-4"
              >
                <div class="w-2 h-2 rounded-full bg-red-400 flex-shrink-0 mt-0.5" style="box-shadow: 0 0 6px rgba(239,68,68,0.8)" aria-hidden="true"></div>
                <p class="type-body text-content-secondary">{{ errorMsg }}</p>
              </div>
            </div>
          </div>

          <!-- Results display -->
          <template v-if="results">
            <!-- Participant header + escape route back to lookup -->
            <div class="mb-8">
              <h1 class="type-page-title mb-1">{{ results.participantName }}</h1>
              <div class="section-rule">
                <span class="section-rule-label">{{ results.eventName }}</span>
                <div class="section-rule-line"></div>
              </div>
              <!-- "how do I go back?" — let the user check another code without reloading -->
              <button
                @click="results = null; refCode = ''; errorMsg = ''"
                class="para-chip-sm px-4 py-2.5 type-label text-content-muted hover:text-content-primary transition-colors mt-4 inline-flex items-center gap-2"
              >
                <i class="pi pi-arrow-left text-xs" aria-hidden="true"></i>
                Look up another code
              </button>
            </div>

            <!-- Genre results -->
            <div
              :class="results.genres.length === 1
                ? 'flex justify-center'
                : 'grid grid-cols-1 sm:grid-cols-2 gap-4'"
            >
              <div
                v-for="genre in results.genres"
                :key="genre.genreName"
                class="card-hover p-4 relative"
                :class="results.genres.length === 1 ? 'w-full max-w-xl' : ''"
              >
                <div class="corner-bar-tl"></div>

                <!-- Genre header -->
                <div class="flex items-center justify-between mb-4">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="badge-neutral">{{ genre.genreName }}</span>
                    <span
                      v-if="genre.format"
                      class="badge-neutral"
                    >{{ genre.format }}</span>
                    <span v-if="genre.auditionNumber" class="type-label text-content-muted">
                      #{{ genre.auditionNumber }}
                    </span>
                  </div>
                  <div v-if="genre.scores && genre.scores.length > 0" class="text-right ml-2 flex-shrink-0">
                    <div class="type-label text-content-muted">Total</div>
                    <div class="type-stat text-[28px]">{{ totalScore(genre.scores) }}</div>
                  </div>
                </div>

                <!-- Scores -->
                <div v-if="genre.scores && genre.scores.length > 0" class="mb-4">
                  <div class="section-rule mb-3">
                    <span class="section-rule-label">Scores</span>
                    <div class="section-rule-line"></div>
                  </div>

                  <template v-if="isMultiCriteria(genre.scores)">
                    <div class="space-y-3">
                      <div
                        v-for="(aspects, judge) in groupScoresByJudge(genre.scores)"
                        :key="judge"
                        class="para-chip px-3 py-2.5"
                      >
                        <div class="type-body text-content-secondary mb-2">{{ judge }}</div>
                        <div class="space-y-1">
                          <div
                            v-for="(score, aspect) in aspects"
                            :key="aspect"
                            class="flex items-center justify-between"
                          >
                            <span class="type-label text-content-muted">{{ aspect }}</span>
                            <span class="type-stat text-[18px]">{{ score }}</span>
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
                        class="para-chip px-3 py-2.5"
                      >
                        <div class="type-label text-content-muted truncate">{{ score.judgeName }}</div>
                        <div class="type-stat text-[22px]">{{ score.score }}</div>
                      </div>
                    </div>
                  </template>
                </div>
                <div v-else class="mb-4">
                  <p class="type-body text-content-muted">No scores recorded yet</p>
                </div>

                <!-- Feedback -->
                <template v-if="genre.feedback && genre.feedback.length > 0">
                  <div class="pt-4" style="border-top: 1px solid rgba(255,255,255,0.07)">
                    <div class="section-rule mb-3">
                      <span class="section-rule-label">Judge Feedback</span>
                      <div class="section-rule-line"></div>
                    </div>
                    <div class="space-y-4">
                      <div
                        v-for="entry in genre.feedback"
                        :key="entry.judgeName"
                        class="para-chip px-3 py-3"
                      >
                        <div class="type-body text-content-secondary mb-2">{{ entry.judgeName }}</div>

                        <template v-if="entry.tags && entry.tags.length > 0">
                          <div
                            v-for="(tags, groupName) in groupTags(entry.tags)"
                            :key="groupName"
                            class="mb-2"
                          >
                            <div class="type-label text-content-muted mb-1">{{ groupName }}</div>
                            <div class="flex flex-wrap gap-1.5">
                              <span
                                v-for="tag in tags"
                                :key="tag.label"
                                class="badge-neutral"
                              >{{ tag.label }}</span>
                            </div>
                          </div>
                        </template>

                        <div v-if="entry.note" class="mt-2 pt-2" style="border-top: 1px solid rgba(255,255,255,0.06)">
                          <p class="type-body text-content-secondary">"{{ entry.note }}"</p>
                        </div>

                        <p
                          v-if="(!entry.tags || entry.tags.length === 0) && !entry.note"
                          class="type-label text-content-muted"
                        >No feedback provided</p>
                      </div>
                    </div>
                  </div>
                </template>
                <div v-else class="pt-4" style="border-top: 1px solid rgba(255,255,255,0.07)">
                  <p class="type-label text-content-muted">No judge feedback for this genre</p>
                </div>
              </div>
            </div>
          </template>

        </div>
      </div>

    </div>
  </div>
</template>
