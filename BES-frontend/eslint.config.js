import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'

export default [
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    rules: {
      'no-unused-vars': ['error', { varsIgnorePattern: '^_', argsIgnorePattern: '^_', caughtErrorsIgnorePattern: '^_' }],
      'no-undef': 'error',
      'vue/no-unused-vars': 'error',

      // Disable HTML formatting/style rules — the existing codebase uses a different style
      // and these are purely cosmetic (not correctness issues).
      'vue/html-indent': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/multiline-html-element-content-newline': 'off',
      'vue/first-attribute-linebreak': 'off',
      'vue/attributes-order': 'off',
      'vue/v-on-event-hyphenation': 'off',
      'vue/no-multi-spaces': 'off',
      'vue/attribute-hyphenation': 'off',
      'vue/html-closing-bracket-spacing': 'off',
      'vue/require-default-prop': 'off',
      'vue/require-prop-types': 'off',
      'vue/no-template-shadow': 'off',
    },
  },
  {
    // Test files run in Node/jsdom with vitest globals — allow `global`
    files: ['src/utils/__tests__/**/*.js', 'src/**/*.test.js', 'src/**/*.spec.js'],
    languageOptions: {
      globals: {
        global: 'writable',
      },
    },
  },
  {
    // Router-level views and single-word utility components are named by route convention.
    // Enforcing multi-word names here would require renaming routes and all imports.
    files: ['src/views/**/*.vue', 'src/components/Timer.vue'],
    rules: {
      'vue/multi-word-component-names': 'off',
    },
  },
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**'],
  },
]
