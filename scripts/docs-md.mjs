#!/usr/bin/env node
// The docs as plain Markdown in docs-md/, which the package ships for AI agents: `apex-navigation mcp` searches and
// reads them, and an agent without it can read the files. One file per page of the site, in its groups, and
// index.json with the order of the site, the titles and the anchors of the sections.
//
// Usage: node scripts/docs-md.mjs   (run by prepack)

import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const { readSite } = require('../cli/mcp/docs.js');

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const out = path.join(root, 'docs-md');
const registry = JSON.parse(fs.readFileSync(path.join(root, 'docs', 'sections.json'), 'utf8'));
const pages = readSite(path.join(root, 'docs', 'content'), registry);

fs.rmSync(out, { recursive: true, force: true });
for (const page of pages) {
  const file = path.join(out, `${page.id}.md`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, page.markdown);
}
const index = pages.map(({ id, title, description, anchors }) => ({
  id,
  title,
  description,
  anchors,
}));
fs.writeFileSync(path.join(out, 'index.json'), JSON.stringify(index, null, 2) + '\n');
console.log(`docs-md: ${pages.length} pages`);
