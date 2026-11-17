<script setup>
defineProps({ name: String, size: { type: Number, default: 18 } });
const paths = {
  spark:
    "m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3M20 2v4m-2-2h4",
  plus: "M12 5v14M5 12h14",
  search: "M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0",
  clock: "M12 8v5l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
  arrow: "M5 12h14m-6-6 6 6-6 6",
  copy: "M9 9h12v12H9zM5 15H3V3h12v2",
  download: "M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5",
  trash: "M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15M10 10v7m4-7v7",
  check: "m5 12 4 4L19 6",
  close: "m6 6 12 12M6 18 18 6",
  settings: "M4 7h16M4 17h16M8 4v6m8 4v6",
  file: "M14 2H4v20h16V8l-6-6v6h6M8 12h8m-8 4h6",
  code: "m8 6-6 6 6 6m8-12 6 6-6 6m-3-15-2 18",
  briefcase: "M3 7h18v14H3zM8 7V3h8v4M3 12c6 4 12 4 18 0M12 12v4",
  message: "M21 3H3v14h5l4 4 4-4h5V3M7 7h10M7 11h7",
  chevron: "m9 5 7 7-7 7",
  monitor: "M2 3h20v14H2zM8 21h8m-4-4v4",
  help: "M9 9a3 3 0 1 1 5 2c-2 1-2 2-2 3m0 3h.01M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0",
};
</script>
<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path :d="paths[name] || paths.spark" />
  </svg>
</template>
