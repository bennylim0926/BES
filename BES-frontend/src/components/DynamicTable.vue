<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  tableValue: {
    type: [Array, Object],
    required: true,
    default: () => ([]),
  },
  tableConfig: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(["update:tableValue", "onClick"]);

const rows = ref([]);

const capsFirst = (text) => {
  return String(text).charAt(0).toUpperCase() + String(text).slice(1);
}

const normalizeData = (data) => {
  if (Array.isArray(data)) {
    return JSON.parse(JSON.stringify(data));
  } else if (data && typeof data === "object") {
    return Object.entries(data).map(([key, value]) => ({ key, value }));
  }
  return [];
};

rows.value = normalizeData(props.tableValue);

watch(
  () => props.tableValue,
  (newVal) => {
    const normalized = normalizeData(newVal);
    if (JSON.stringify(normalized) !== JSON.stringify(rows.value)) {
      rows.value = normalized;
    }
  },
  { deep: true, immediate: true }
);

watch(
  rows,
  (newVal) => {
    if (Array.isArray(props.tableValue)) {
      emit("update:tableValue", JSON.parse(JSON.stringify(newVal)));
    } else if (props.tableValue && typeof props.tableValue === "object") {
      const objVal = {};
      newVal.forEach((r) => objVal[r.key] = r.value);
      emit("update:tableValue", objVal);
    }
  },
  { deep: true }
);

const getConfig = (key) => props.tableConfig.find(c => c.key === key) || { type: "text" };
</script>

<template>
  <div class="w-full overflow-x-auto rounded-xl border border-surface-200/80 shadow-sm">
    <table class="min-w-full text-sm text-surface-900">
      <thead>
        <tr class="bg-surface-800 text-white">
          <th
            v-for="col in props.tableConfig"
            :key="col.key"
            class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider whitespace-nowrap"
          >
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody class="divide-y divide-surface-100">
        <tr
          v-for="(row, rowIndex) in rows"
          :key="rowIndex"
          class="bg-white even:bg-surface-50 hover:bg-primary-50 transition-colors duration-150"
        >
          <td
            v-for="col in props.tableConfig"
            :key="col.key"
            class="px-4 py-3 whitespace-nowrap"
          >
            <!-- Read-only -->
            <template v-if="col.readonly">
              <span class="text-surface-700">
                {{ row[col.key] !== null && row[col.key] !== undefined && row[col.key] !== ''
                  ? capsFirst(row[col.key])
                  : '—' }}
              </span>
            </template>

            <!-- Link -->
            <template v-else-if="col.type === 'link'">
              <button
                @click="emit('onClick', row[col.key])"
                class="text-primary-600 hover:text-primary-700 font-medium hover:underline focus:outline-none"
              >
                {{ row[col.key] }}
              </button>
            </template>

            <!-- Editable text -->
            <template v-else-if="col.type === 'text'">
              <input
                v-model="row[col.key]"
                type="text"
                class="input-base text-sm py-1.5 px-3"
              />
            </template>

            <!-- Editable number -->
            <template v-else-if="col.type === 'number'">
              <input
                v-model.number="row[col.key]"
                type="number"
                class="input-base text-sm py-1.5 px-3 w-24"
              />
            </template>

            <!-- Editable select -->
            <template v-else-if="col.type === 'select'">
              <select
                v-model="row[col.key]"
                class="input-base text-sm py-1.5 px-3 pr-8"
              >
                <option v-for="opt in col.options" :key="opt" :value="opt">
                  {{ opt ? capsFirst(opt) : '— None —' }}
                </option>
              </select>
            </template>

            <!-- Boolean toggle -->
            <template v-else-if="col.type === 'boolean'">
              <button
                @click="row[col.key] = !row[col.key]"
                class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary-500/30"
                :class="row[col.key] ? 'bg-primary-500' : 'bg-surface-300'"
              >
                <span
                  class="inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform duration-200"
                  :class="row[col.key] ? 'translate-x-6' : 'translate-x-1'"
                ></span>
              </button>
            </template>

            <!-- Fallback -->
            <template v-else>
              <span class="text-surface-700">{{ row[col.key] }}</span>
            </template>
          </td>
        </tr>

        <!-- Empty state -->
        <tr v-if="rows.length === 0">
          <td
            :colspan="props.tableConfig.length"
            class="px-4 py-10 text-center text-surface-400 text-sm"
          >
            <i class="pi pi-inbox text-2xl block mb-2 opacity-40"></i>
            No data available
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
