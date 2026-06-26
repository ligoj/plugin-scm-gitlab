/*
 * Service layer for plugin "scm-gitlab".
 *
 * Tool-level plugin (lives at `service:scm:gitlab`). The parent
 * `plugin-scm` delegates the subscription-row hooks to us via its
 * `subPluginIdFor` delegation. Mirrors the legacy `gitlab.js`:
 *
 *   - renderFeatures   → a home link to the GitLab project
 *     (`<url>/<user>/<repository>`, default host `https://gitlab.com`).
 *   - renderDetailsKey → the repository chip
 *     (`service:scm:gitlab:repository`).
 *
 * The legacy live issues/stars/forks counts read `subscription.data`
 * and are omitted here, like the other live-data carousels.
 *
 * Kept free of Vue SFC imports so it can be unit-tested without a DOM.
 */
import { renderServiceLink, renderDetailsChip, useI18nStore } from '@ligoj/host'

const PARAM_URL = 'service:scm:gitlab:url'
const PARAM_USER = 'service:scm:gitlab:user'
const PARAM_REPO = 'service:scm:gitlab:repository'

/** Default GitLab host, used when no self-hosted base URL is configured. */
const DEFAULT_URL = 'https://gitlab.com'

/** GitLab project home link. Mirrors the legacy renderFeatures(). */
function renderFeatures(subscription) {
  const params = subscription?.parameters
  const user = params?.[PARAM_USER]
  const repo = params?.[PARAM_REPO]
  if (!user || !repo) return []
  const { t } = useI18nStore()
  const base = (params[PARAM_URL] || DEFAULT_URL).replace(/\/+$/, '')
  return [renderServiceLink({ icon: 'mdi-gitlab', href: `${base}/${user}/${repo}`, title: t('service:scm:gitlab:repository') })]
}

/** Repository chip. Mirrors the legacy renderKey('service:scm:gitlab:repository'). */
function renderDetailsKey(subscription) {
  const repo = subscription?.parameters?.[PARAM_REPO]
  if (!repo) return null
  const { t } = useI18nStore()
  return renderDetailsChip({ icon: 'mdi-gitlab', text: repo, title: t('service:scm:gitlab:repository') })
}

export default { renderFeatures, renderDetailsKey }
