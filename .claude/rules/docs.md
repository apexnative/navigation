---
paths:
  - 'docs/**'
---

# The docs site

- Docusaurus, its own Yarn project (`docs/yarn.lock`, not a workspace). `scripts/docs.sh [api | typecheck | build]`
  checks it; `yarn --cwd docs start` runs the site (port 3000).
- Pages: `docs/content/<group>/<section>.mdx`; the folder is the group (`start`, `fundamentals`, `containers`,
  `screen-ui`, `architecture`, `flows`, `guides`, `migration`, `help`).
- `docs/sections.json` lists the groups and sections. A section has `id`, `title`, and either `guide: true` (prose
  only) or `status`, with `showcase` (scenario ids), `pages` (exports with a page of their own), `types`, `options`
  (option roots such as `topBar` or `topBar.rightButtons`). The sidebars read it.
- `docs/scripts/api.mjs` (`scripts/docs.sh api`) builds the API reference from the types of `src/index.ts`: it fails
  when an export is in no section or in two, and links scenarios of the Showcase. Reference pages of exports are written by hand in
  `docs/api/<section>/<Export>.mdx`; the run fails when one leaves out a member.
- `docs/reference/` and `docs/.api/` are generated and ignored by git: never edit them.
- Components of the site: `docs/src/components/` (`Api`, `Intro`, `Screens`, `Search`, `Terminal`, `PackageInstall`);
  theme overrides in `docs/src/theme/`.
- Writing a page: `.claude/skills/docs/writing.md` (the `docs` skill). Think the subject through first (what breaks, each
  screen a React root, platforms, other libraries), the situation and an example before the rule, and why. Code lines at most 72 characters. Check the page in the dark theme too.
