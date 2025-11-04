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

const emit = defineEmits(["update:tableValue", "onClick"]);

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
    <div class="flex justify-center mb-3">
      <!-- Outer wrapper makes table scrollable on small devices -->
      <div class="w-full overflow-x-auto max-h-130 rounded-lg shadow-lg">
        <table class="min-w-full sm:w-auto text-xl md:text-2xl lg:text-2xl  text-black mb-5">
          <thead
            class="text-xs text-black uppercase bg-orange-400 sticky top-0"
          >
            <tr>
              <th
                v-for="col in props.tableConfig"
                :key="col.key"
                class="px-4 sm:px-6 text-lg py-3 text-center whitespace-nowrap text-white"
              >
                {{ col.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, rowIndex) in rows"
              :key="rowIndex"
              class="bg-orange-50 
                     hover:bg-gray-200 "
            >
              <td
                v-for="col in props.tableConfig"
                :key="col.key"
                class="px-4 sm:px-6 py-4 text-center whitespace-nowrap text-black "
              >
                <!-- Read-only -->
                <template v-if="col.readonly" class="text-2xl">
                  {{ row[col.key] !== null && row[col.key] !== undefined && row[col.key] !== '' 
                    ? capsFirst(row[col.key]) 
                    : '-' }}
                </template>

                <template v-else-if="col.type === 'link'">
                  <div @click="emit('onClick', row[col.key])"> {{ row[col.key] }}</div>
                </template>
  
                <!-- Editable text -->
                <template v-else-if="col.type === 'text'">
                  <input
                    v-model="row[col.key]"
                    type="text"
                    class="border rounded p-1 w-full"
                  />
                </template>
  
                <!-- Editable number -->
                <template v-else-if="col.type === 'number'">
                  <input
                    v-model.number="row[col.key]"
                    type="number"
                    class="border rounded p-1 w-full"
                  />
                </template>
  
                <!-- Editable select -->
                <template v-else-if="col.type === 'select'">
                  <select v-model="row[col.key]" class="border rounded p-1 w-full">
                    <option
                      class="text-center"
                      v-for="opt in col.options"
                      :key="opt"
                      :value="opt"
                    >
                      {{ capsFirst(opt) }}
                    </option>
                  </select>
                </template>

                <template v-else-if="col.type === 'boolean'">
                  <div
                    @click="row[col.key] = !row[col.key]"
                    class="w-12 h-12 mx-auto rounded-full active:border-4 active:border-green-500 active:bg-green-300"
                    :class="row[col.key] ? 'bg-green-500' : 'bg-gray-200'"
                  ></div>
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
    </div>
    <div class="flex justify-center items-center">
      <p class="text-xl font-semibold text-black">-End of List-</p>
    </div>
  </template>
  
