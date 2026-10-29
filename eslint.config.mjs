import { fixupConfigRules } from '@eslint/compat';
import { FlatCompat } from '@eslint/eslintrc';
import js from '@eslint/js';
import prettier from 'eslint-plugin-prettier';
import { defineConfig } from 'eslint/config';
import globals from 'globals';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
  allConfig: js.configs.all,
});

export default defineConfig([
  {
    extends: fixupConfigRules(compat.extends('@react-native', 'prettier')),
    plugins: { prettier },
    rules: {
      'react/react-in-jsx-scope': 'off',
      'prettier/prettier': 'error',
    },
  },
  {
    // Imported sources keep their original formatting for now.
    files: ['src/**/*.{ts,tsx}', 'android/**/*.js'],
    rules: {
      'prettier/prettier': 'warn',
    },
  },
  {
    // Showcase screens use theme-dependent colors (light/dark), applied inline.
    files: ['example/**/*.{js,ts,tsx}'],
    rules: {
      'react-native/no-inline-styles': 'off',
    },
  },
  {
    // Repository tooling: ES modules run by Node.
    files: ['**/*.mjs'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: globals.node,
    },
  },
  {
    // The CLI and the Metro setup: CommonJS run by Node.
    files: ['cli/**/*.js', 'metro/**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'commonjs',
      globals: { ...globals.node, ...globals.jest },
    },
  },
  {
    ignores: [
      'node_modules/',
      'lib/',
      'coverage/',
      '**/build/',
      'cli/**/__fixtures__/',
      'docs/.docusaurus/',
    ],
  },
]);
