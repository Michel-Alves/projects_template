import { defineConfig } from 'vitest/config';
import swc from 'unplugin-swc';

export default defineConfig({
  test: {
    globals: true,
    passWithNoTests: true,
    projects: [
      {
        test: {
          name: 'unit',
          include: ['src/**/*.spec.ts'],
          environment: 'node',
          passWithNoTests: true,
        },
      },
      {
        test: {
          name: 'integration',
          include: ['test/**/*.integration.test.ts'],
          environment: 'node',
          testTimeout: 120_000,
          hookTimeout: 120_000,
          passWithNoTests: true,
        },
      },
    ],
  },
  plugins: [
    swc.vite({
      module: { type: 'es6' },
    }),
  ],
});
