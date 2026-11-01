import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const rootDir = path.resolve(__dirname, '..')

describe('Frontend CI Workflow Configuration', () => {
  test('package.json defines all required CI lifecycle scripts', () => {
    const pkgPath = path.join(rootDir, 'package.json')
    assert.ok(fs.existsSync(pkgPath), 'package.json must exist in frontend root')

    const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'))
    assert.equal(pkg.type, 'module', 'package.json must specify ES module type')

    const { scripts } = pkg
    assert.ok(scripts, 'package.json must define scripts')
    assert.equal(scripts.lint, 'eslint .', 'CI requires lint script to execute eslint')
    assert.equal(scripts.test, 'node --test', 'CI requires test script to run node --test')
    assert.equal(scripts.build, 'vite build', 'CI requires build script to compile production bundle')
  })

  test('package.json declares critical build and runtime dependencies', () => {
    const pkgPath = path.join(rootDir, 'package.json')
    const pkg = JSON.parse(fs.readFileSync(pkgPath, 'utf-8'))

    const { dependencies, devDependencies } = pkg
    assert.ok(dependencies, 'dependencies must be defined')
    assert.ok(devDependencies, 'devDependencies must be defined')

    assert.ok(dependencies.react, 'react dependency must be present')
    assert.ok(dependencies['react-dom'], 'react-dom dependency must be present')
    assert.ok(dependencies.axios, 'axios dependency must be present')
    assert.ok(devDependencies.vite, 'vite devDependency must be present')
    assert.ok(devDependencies.eslint, 'eslint devDependency must be present')
  })

  test('installed node_modules are resolvable without missing packages', async () => {
    // Verifies dependency installation succeeded by importing core packages
    const react = await import('react')
    assert.ok(react.default || react.createElement, 'React must be resolvable from node_modules')

    const axios = await import('axios')
    assert.ok(axios.default, 'Axios must be resolvable from node_modules')
  })
})

describe('CI Test Runner Failure and Assertion Coverage', () => {
  test('assertion failures throw AssertionError to ensure CI fails on errors', () => {
    assert.throws(
      () => {
        assert.equal('actual-value', 'expected-value', 'Synthetic mismatch to verify error throwing')
      },
      {
        name: 'AssertionError',
      },
      'Assertion failures must propagate as AssertionError so that CI detects test failures',
    )
  })

  test('asynchronous rejection is properly captured by the test runner', async () => {
    await assert.rejects(
      async () => {
        throw new Error('Async failure simulation')
      },
      {
        name: 'Error',
        message: 'Async failure simulation',
      },
      'Async rejections must be captured to ensure async regressions fail CI runs',
    )
  })
})

describe('Build and Deployment Configuration Validation', () => {
  test('vite.config.js is present and exports valid configuration', async () => {
    const viteConfigPath = path.join(rootDir, 'vite.config.js')
    assert.ok(fs.existsSync(viteConfigPath), 'vite.config.js must exist')

    const configModule = await import(`file://${path.resolve(viteConfigPath)}`)
    assert.ok(configModule.default, 'vite.config.js must have a default export')
  })

  test('index.html entry point is valid and mounts root element', () => {
    const indexPath = path.join(rootDir, 'index.html')
    assert.ok(fs.existsSync(indexPath), 'index.html entrypoint must exist')

    const html = fs.readFileSync(indexPath, 'utf-8')
    assert.ok(html.includes('id="root"'), 'index.html must define the root mount element')
    assert.ok(html.includes('src="/src/main.jsx"'), 'index.html must reference the main.jsx entrypoint')
  })
})
