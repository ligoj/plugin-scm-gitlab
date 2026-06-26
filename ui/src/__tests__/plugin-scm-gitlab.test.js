/*
 * Contract tests for plugin-scm-gitlab, incl. the parent → child delegation:
 * when scm-gitlab is registered, plugin-scm's renderFeatures/renderDetailsKey
 * resolve to this tool for a matching node.
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { pluginRegistry, useI18nStore } from '@ligoj/host'
import def from '../index.js'
import parentDef from '../../../../plugin-scm/ui/src/index.js'

beforeEach(() => { setActivePinia(createPinia()) })

describe('plugin-scm-gitlab manifest', () => {
  it('exposes a valid tool-level manifest', () => {
    expect(def.id).toBe('scm-gitlab')
    expect(def.label).toBe('GitLab')
    expect(def.requires).toEqual(['scm'])
    expect(def.routes).toBeUndefined()
    expect(def.component).toBeUndefined()
    expect(typeof def.install).toBe('function')
    expect(typeof def.feature).toBe('function')
    expect(def.service).toBeTypeOf('object')
    expect(def.meta).toMatchObject({ icon: expect.any(String), color: expect.any(String) })
  })

  it('merges en + fr i18n on install', () => {
    const i18n = useI18nStore()
    def.install()
    expect(i18n.t('service:scm:gitlab:repository')).toBeTypeOf('string')
    expect(i18n.t('service:scm:gitlab:repository')).not.toBe('service:scm:gitlab:repository')
    i18n.setLocale('fr')
    expect(i18n.t('service:scm:gitlab:url')).toBe('URL de base')
  })

  it('throws for an unknown feature', () => {
    expect(() => def.feature('nope')).toThrow(/Plugin "scm-gitlab" has no feature "nope"/)
  })

  it('renderFeatures returns a home-link to the configured base URL', () => {
    def.install()
    const vnodes = def.feature('renderFeatures', {
      parameters: {
        'service:scm:gitlab:url': 'https://git.acme.io/',
        'service:scm:gitlab:user': 'group',
        'service:scm:gitlab:repository': 'app',
      },
    })
    expect(vnodes).toHaveLength(1)
    expect(vnodes[0].__v_isVNode).toBe(true)
    expect(vnodes[0].props.target).toBe('_blank')
    // Trailing slashes trimmed; <url>/<user>/<repo>.
    expect(vnodes[0].props.href).toBe('https://git.acme.io/group/app')
  })

  it('renderFeatures falls back to gitlab.com when no base URL is set', () => {
    def.install()
    const vnodes = def.feature('renderFeatures', {
      parameters: {
        'service:scm:gitlab:user': 'acme',
        'service:scm:gitlab:repository': 'app',
      },
    })
    expect(vnodes).toHaveLength(1)
    expect(vnodes[0].props.href).toBe('https://gitlab.com/acme/app')
  })

  it('renderFeatures returns [] without the required params', () => {
    def.install()
    // missing repository
    expect(def.feature('renderFeatures', { parameters: { 'service:scm:gitlab:user': 'acme' } })).toEqual([])
    // missing user
    expect(def.feature('renderFeatures', { parameters: { 'service:scm:gitlab:repository': 'app' } })).toEqual([])
    // empty parameters
    expect(def.feature('renderFeatures', { parameters: {} })).toEqual([])
    // no parameters at all
    expect(def.feature('renderFeatures', {})).toEqual([])
  })

  it('renderDetailsKey returns the repository chip when present, else null', () => {
    def.install()
    const chip = def.feature('renderDetailsKey', { parameters: { 'service:scm:gitlab:repository': 'app' } })
    expect(chip).toBeTruthy()
    expect(chip.__v_isVNode).toBe(true)
    expect(def.feature('renderDetailsKey', { parameters: {} })).toBeNull()
    expect(def.feature('renderDetailsKey', {})).toBeNull()
  })
})

describe('plugin-scm → plugin-scm-gitlab delegation', () => {
  beforeEach(() => {
    parentDef.install({ router: { addRoute() {} } })
    def.install()
    pluginRegistry.register('scm-gitlab', def)
  })
  afterEach(() => { pluginRegistry.remove('scm-gitlab') })

  it('parent renderFeatures resolves to this tool for a matching node', () => {
    const out = parentDef.feature('renderFeatures', {
      node: { id: 'service:scm:gitlab:1' },
      parameters: { 'service:scm:gitlab:user': 'acme', 'service:scm:gitlab:repository': 'app' },
    })
    expect(Array.isArray(out)).toBe(true)
    expect(out.length).toBe(1)
    expect(out[0].__v_isVNode).toBe(true)
  })

  it('parent renderDetailsKey resolves to this tool for a matching node', () => {
    const out = parentDef.feature('renderDetailsKey', {
      node: { id: 'service:scm:gitlab:1' },
      parameters: { 'service:scm:gitlab:repository': 'app' },
    })
    expect(Array.isArray(out)).toBe(true)
    expect(out.length).toBe(1)
    expect(out[0].__v_isVNode).toBe(true)
  })

  it('does not delegate for a different tool', () => {
    const out = parentDef.feature('renderDetailsKey', {
      node: { id: 'service:scm:other:1' },
      parameters: {},
    })
    expect(out).toBeNull()
  })
})
