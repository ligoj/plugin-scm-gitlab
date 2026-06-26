# :link: Ligoj GitLab plugin ![Maven Central](https://img.shields.io/maven-central/v/org.ligoj.plugin/plugin-scm-gitlab)

[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=org.ligoj.plugin%3Aplugin-scm-gitlab&metric=coverage)](https://sonarcloud.io/dashboard?id=org.ligoj.plugin%3Aplugin-scm-gitlab)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?metric=alert_status&project=org.ligoj.plugin:plugin-scm-gitlab)](https://sonarcloud.io/dashboard/index/org.ligoj.plugin:plugin-scm-gitlab)
[![License](http://img.shields.io/:license-mit-blue.svg)](http://fabdouglas.mit-license.org/)

[Ligoj](https://github.com/ligoj/ligoj) GitLab plugin, extending the [SCM plugin](https://github.com/ligoj/plugin-scm).

Tool-level plugin living at the node `service:scm:gitlab`. Like its GitHub
sibling it augments the SCM service parent with a subscription-row home link to
the GitLab project and a repository chip. Unlike GitHub it is self-hostable, so
it carries a base URL parameter (defaulting to `https://gitlab.com`).

Provides the following features:
- Repository home link (`<base-url>/<user>/<repository>`)
- Repository chip in the subscription details
- Open issues / stars / forks counts + contributors (subscription status)
- Self-hosted GitLab support via a node-level base URL

## Node parameters

These are the node parameters consumed by the subscription wizard, the
backend validation and the row-render hooks. Labels ship in the plugin's i18n
bundle (`ui/src/i18n/`); types/flags are declared in
[`src/main/resources/csv/parameter.csv`](src/main/resources/csv/parameter.csv).

| Parameter                          | Type   | Mandatory | Secured | Purpose                                          |
| ---------------------------------- | ------ | --------- | ------- | ------------------------------------------------ |
| `service:scm:gitlab:url`           | `TEXT` | yes       | no      | Base URL of the GitLab instance (self-hosted).   |
| `service:scm:gitlab:user`          | `TEXT` | yes       | no      | User or group namespace owning the project.      |
| `service:scm:gitlab:repository`    | `TEXT` | yes       | no      | Project (repository) name.                       |
| `service:scm:gitlab:auth-key`      | `TEXT` | yes       | yes     | Personal access token / authentication key.      |

The Vue subscription-row home link falls back to `https://gitlab.com` if the
`url` parameter is unset.

## Backend (Java) module

`GitlabPluginResource` derives the GitLab API base from the node `url`
(`<url>/api/v4`) and authenticates with a `PRIVATE-TOKEN` header. It validates
the node (`GET /api/v4/user`) and the subscription repository
(`GET /api/v4/projects?search=<repository>`, matched on `path_with_namespace`),
and exposes issues / stars / forks / contributors as subscription status.

```bash
mvn -Pjacoco verify     # JUnit (WireMock-backed) + JaCoCo (100% coverage)
```

## UI (Vue) module

The Vue source for this plugin lives in `ui/` and is built into the plugin JAR
under `META-INF/resources/webjars/scm-gitlab/vue/`. See
[`REWRITE_VUEJS.md`](https://github.com/ligoj/ligoj) for the migration recipe.

```bash
cd ui
npm install
npm run build          # emits to ../src/main/resources/.../webjars/scm-gitlab/vue/
npm run lint
npm test               # vitest run — contract + delegation tests
npm run test:coverage  # enforces 100% statements / branches / functions / lines
```
