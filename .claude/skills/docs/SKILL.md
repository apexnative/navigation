---
name: docs
description: Everything about this project's documentation — the Docusaurus site in docs/ (pages, sections.json, the API reference generator, extras), the ARCHITECTURE.md files, the CLAUDE.md maps and the README — how it is organized, how a page is written so a reader learns from it quickly, how a page is audited, and how it is checked. Use when writing, updating or auditing documentation, and after a change of the public API.
argument-hint: <what to write or update> | audit <page or group>
---

# Documentation

## What lives where

- **The site:** `docs/content/<group>/<section>.mdx`. Groups: `start`, `fundamentals`, `containers`, `screen-ui`,
  `architecture`, `flows`, `guides`, `migration`, `help`.
- **`docs/sections.json`:** groups → sections (`id`, `title`, `guide: true` or `status`, `showcase`, `pages`, `types`,
  `options`, `memberGroups`). Every export of `src/index.ts` sits in exactly one section.
- **The API reference:** written by hand in `docs/api/<section>/<Export>.mdx` — a heading per member the export
  takes or returns, `<ApiMember>` under it for the type, default and platforms. `docs/scripts/api.mjs` builds
  `docs/reference/` and `docs/.api/` from it and from the types: the generated files are never edited. The run fails
  when a written page leaves out a member, and lists the exports whose page isn't written yet (those still show the
  JSDoc alone, with `docs/extras/<section>/<page>.<slot>.mdx` added). Options and types pages stay generated from
  the JSDoc.
- **Architecture:** `ARCHITECTURE.md`, `src/ARCHITECTURE.md` (deep); `CLAUDE.md`, `ios/CLAUDE.md`,
  `android/CLAUDE.md` (maps, loaded by Claude Code with every file of their folder: keep them short);
  `ios/CORE.md`, `android/CORE.md` (layers, flows, the single places, modules) and `ios/RECIPES.md`,
  `android/RECIPES.md` (adding an option or a command), read on demand; `README.md`.
- **Site code:** components in `docs/src/components/` (`Api`, `Intro`, `Screens`, `Search`, `Terminal`,
  `PackageInstall`), theme overrides in `docs/src/theme/`, styles and tokens in `docs/src/css/custom.css`.

## Writing a page

`writing.md` next to this file, modeled on the docs of React Native and React Navigation: the reader, **Think first**
(the situation, what breaks, each screen a React root, the platforms, time, the what-ifs, other libraries), the kinds
of pages (tutorial, concept, guide, reference), code. Read it before writing or changing a page. A page is thought
through, not filled from a template.

- Look: the site's own components and tokens; no new colors or fonts. Overview cards lead to sections of the same page.
- The Credits block (the README's `## Credits` and the end of `docs/content/start/introduction.mdx`) has a fixed text:
  don't change it.

## Auditing

`audit <page or group>`: follow `audit.md` next to this file. It reports findings and changes nothing; the fixes come
after the user's answers.

## After a code change

List what the change makes wrong: JSDoc, `sections.json`, the pages that show the changed API, the architecture
files and maps when the structure changed. Update them in the same task.

## Versions and the banner

- The docs describe the version of the root `package.json`: the navbar's version and the version of both docs
  instances (Docs and the API reference) come from it in `docs/docusaurus.config.ts`. While it is a pre-release
  (`-alpha`, `-beta`, `-rc`), every page of both carries the banner of `docs/src/theme/DocVersionBanner`: the stage,
  what it promises (`STAGES`, the same stages as a release) and the install command with its npm tag. A stable version
  has no banner. Nothing to do by hand when the version changes.
- A new major after a stable release (2.0.0-alpha.0 after 1.x): before the version in `package.json` changes, keep the
  released docs with `yarn --cwd docs docusaurus docs:version <x.y.z>` and `docs:version:api <x.y.z>`, and set
  `lastVersion` of both instances to it. `/docs` and `/api` then show the released version, the new pre-release moves
  to `/docs/next` and `/api/next`, and its banner links to the same page of the released one. The navbar's version
  button becomes a `docsVersionDropdown` once there are two versions.
- A kept version that no longer gets updates: `banner: 'unmaintained'` for it in `versions`; its banner says so and
  links to the latest.

## Check

`scripts/docs.sh api` and `scripts/docs.sh typecheck`; `scripts/docs.sh build` after a page changed. A pull request
builds the site the same way (`docs.yml`); a merge into `main` publishes it at navigation.apexnative.dev.

## The result

The files changed, what changed in each, the checks' lines.
