<script setup>
import { ref, watch } from "vue";

const props = defineProps({
  tableValue: {
    type: [Array, Object],
    required: true,
    default: () => ([]), // fallback to empty array if null
  },
  tableConfig: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(["update:tableValue"]);

const rows = ref([]);

const capsFirst = (text) =>{
    return String(text).charAt(0).toUpperCase() + String(text).slice(1);
}

// normalize incoming data safely
const normalizeData = (data) => {
  if (Array.isArray(data)) {
    return JSON.parse(JSON.stringify(data)); // deep copy
  } else if (data && typeof data === "object") {
    return Object.entries(data).map(([key, value]) => ({ key, value }));
  }
  return [];
};

// initialize rows
rows.value = normalizeData(props.tableValue);

// watch incoming prop safely
watch(
  () => props.tableValue,
  (newVal) => {
    const normalized = normalizeData(newVal);
    // only update if changed to prevent infinite loop
    if (JSON.stringify(normalized) !== JSON.stringify(rows.value)) {
      rows.value = normalized;
    }
    // console.log(props.tableValue)
    // console.log(rows.value)
  },
  { deep: true, immediate: true }
);

// watch rows and emit only if really changed
watch(
  rows,
  (newVal) => {
    // Convert back to array or object format
    if (Array.isArray(props.tableValue)) {
      const arrVal = JSON.parse(JSON.stringify(newVal));
      emit("update:tableValue", arrVal);
    } else if (props.tableValue && typeof props.tableValue === "object") {
      const objVal = {};
      newVal.forEach((r) => objVal[r.key] = r.value);
      emit("update:tableValue", objVal);
    }
  },
  { deep: true }
);

// helper to get column config by key
const getConfig = (key) => props.tableConfig.find(c => c.key === key) || { type: "text" };
</script>

<template>
  <div class="flex justify-center overflow-x-auto sm:rounded-lg mb-3">
    <table class="w-auto text-sm text-gray-500 border border-gray-200 rounded-lg overflow-hidden">
      <thead class="text-xs text-gray-700 uppercase bg-gray-50 dark:bg-gray-700 dark:text-gray-400">
        <tr>
          <th v-for="col in props.tableConfig" :key="col.key" class="px-30 py-3">
            {{ col.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, rowIndex) in rows"
          :key="rowIndex"
          class="bg-white border-b dark:bg-gray-800 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-600"
        >
          <td v-for="col in props.tableConfig" :key="col.key" class="px-6 py-4 text-center">
            
            <!-- Read-only -->
            <template v-if="col.readonly">
              {{ capsFirst(row[col.key]) }}
            </template>

            <!-- Editable text -->
            <template v-else-if="col.type === 'text'">
              <input v-model="row[col.key]" type="text" class="border rounded p-1 w-full" />
            </template>

            <!-- Editable number -->
            <template v-else-if="col.type === 'number'">
              <input v-model.number="row[col.key]" type="number" class="border rounded p-1 w-full" />
            </template>

            <!-- Editable select -->
            <template v-else-if="col.type === 'select'">
              <select v-model="row[col.key]" class="border rounded p-1 w-full">
                <option class="text-center" v-for="opt in col.options" :key="opt" :value="opt">{{ capsFirst(opt) }}</option>
              </select>
            </template>

            <!-- fallback -->
            <template v-else>
              {{ row[col.key] }}
            </template>

          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>
