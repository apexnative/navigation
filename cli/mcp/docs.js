// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const { sectionsOf, toMarkdown } = require('./markdown');

/** @typedef {import('./markdown').DocPage} DocPage */

// The package: docs-md/ is in it once published; in its repository the pages are read from docs/content.
const PACKAGE_ROOT = path.join(__dirname, '..', '..');
// At most this many characters of a page in one answer: an agent reads a section of a longer page.
const MAX_PAGE = 40_000;

/** @type {DocPage[] | undefined} */
let pages;

/**
 * The pages of the docs, in the order of the site: the Markdown the package ships (`docs-md/<group>/<section>.md` with
 * `docs-md/index.json`, made by `scripts/docs-md.mjs`), or, in the repository of the navigation, its pages made on the spot.
 * @param {string} [root] the package
 * @returns {DocPage[]}
 */
function loadDocs(root = PACKAGE_ROOT) {
  if (pages && root === PACKAGE_ROOT) return pages;
  const built = path.join(root, 'docs-md', 'index.json');
  /** @type {DocPage[]} */
  let found = [];
  if (fs.existsSync(built)) {
    /** @type {Omit<DocPage, 'markdown' | 'sections'>[]} */
    const index = JSON.parse(fs.readFileSync(built, 'utf8'));
    found = index.map((entry) => {
      const markdown = fs.readFileSync(path.join(root, 'docs-md', `${entry.id}.md`), 'utf8');
      return { ...entry, markdown, sections: sectionsOf(markdown, entry.anchors) };
    });
  } else {
    const content = path.join(root, 'docs', 'content');
    const sections = path.join(root, 'docs', 'sections.json');
    if (fs.existsSync(sections)) {
      found = readSite(content, JSON.parse(fs.readFileSync(sections, 'utf8')));
    }
  }
  if (root === PACKAGE_ROOT) pages = found;
  return found;
}

/**
 * The pages of the site from its sources, in the order of sections.json; pages it doesn't list come after.
 * @param {string} content docs/content
 * @param {{ groups: { id: string; sections: { id: string }[] }[] }} registry
 * @returns {DocPage[]}
 */
function readSite(content, registry) {
  const ids = registry.groups.flatMap((group) =>
    group.sections.map((section) => `${group.id}/${section.id}`)
  );
  const all = fs
    .readdirSync(content, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .flatMap((group) =>
      fs
        .readdirSync(path.join(content, group.name))
        .filter((name) => name.endsWith('.mdx'))
        .map((name) => `${group.name}/${name.replace(/\.mdx$/, '')}`)
    );
  const ordered = [
    ...ids.filter((id) => all.includes(id)),
    ...all.filter((id) => !ids.includes(id)),
  ];
  return ordered.map((id) =>
    toMarkdown(id, fs.readFileSync(path.join(content, `${id}.mdx`), 'utf8'))
  );
}

/**
 * The sections of the docs that answer a query best: each word of it counts where it is, a heading most, then the
 * page's title, then the text. A line with a word of the query comes with each.
 * @param {DocPage[]} docs
 * @param {string} query
 * @param {number} [limit]
 * @returns {{ page: string; title: string; section: string; anchor: string; line: string }[]}
 */
function searchDocs(docs, query, limit = 8) {
  const words = query
    .toLowerCase()
    .split(/[^\p{L}\p{N}_.-]+/u)
    .filter((word) => word.length > 1);
  if (words.length === 0) return [];
  const moving = /migrat|moving|react-navigation|react-native-navigation|expo|\bfrom\b/.test(
    query.toLowerCase()
  );
  const count = (/** @type {string} */ text, /** @type {string} */ word) =>
    text.split(word).length - 1;
  const results = [];
  for (const page of docs) {
    const title = page.title.toLowerCase();
    for (const section of page.sections) {
      const heading = section.heading.toLowerCase();
      const text = section.text.toLowerCase();
      let score = 0;
      for (const word of words) {
        score += count(heading, word) * 6 + count(title, word) * 8 + Math.min(count(text, word), 5);
      }
      // Every word of the query in the section counts more than one word many times.
      if (words.every((word) => heading.includes(word) || text.includes(word))) score *= 2;
      // The pages of moving from another library repeat every topic: they come first only when the query is about it.
      if (page.id.startsWith('migration/') && !moving) score /= 2;
      if (score === 0) continue;
      const line =
        section.text
          .split('\n')
          .find((candidate) => words.some((word) => candidate.toLowerCase().includes(word)))
          ?.trim()
          .slice(0, 200) ?? '';
      results.push({
        score,
        page: page.id,
        title: page.title,
        section: section.heading,
        anchor: section.anchor,
        line,
      });
    }
  }
  return results
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map(({ page, title, section, anchor, line }) => ({ page, title, section, anchor, line }));
}

/**
 * A page of the docs, or one of its sections, as Markdown: by its id (`start/installation`), the id of its section
 * of the site (`installation`), or its title. A section by its anchor or heading takes what is under it, up to the next
 * heading of its level. Undefined when there is no such page or section.
 * @param {DocPage[]} docs
 * @param {string} page
 * @param {string} [section]
 * @returns {{ page: string; title: string; markdown: string; sections: string[] } | undefined}
 */
function readDoc(docs, page, section) {
  const wanted = page.toLowerCase().replace(/^\/|\.mdx?$/g, '');
  const found =
    docs.find(({ id }) => id === wanted) ??
    docs.find(({ id }) => id.split('/')[1] === wanted) ??
    docs.find(({ title }) => title.toLowerCase() === wanted);
  if (!found) return undefined;
  const anchors = found.sections.map(({ anchor, heading }) => `${anchor}: ${heading}`);
  if (!section) {
    const markdown =
      found.markdown.length > MAX_PAGE
        ? `${found.markdown.slice(0, MAX_PAGE)}\n\n[The page goes on: read one of its sections.]\n`
        : found.markdown;
    return { page: found.id, title: found.title, markdown, sections: anchors };
  }
  const key = section.toLowerCase().replace(/^#/, '');
  const index = found.sections.findIndex(
    ({ anchor, heading }) => anchor === key || heading.toLowerCase() === key
  );
  if (index < 0) return undefined;
  const { heading, level, text } = found.sections[index];
  // The subsections under it belong to it.
  const nested = [];
  for (const next of found.sections.slice(index + 1)) {
    if (next.level <= level) break;
    nested.push(`${'#'.repeat(next.level)} ${next.heading}\n\n${next.text}`);
  }
  return {
    page: found.id,
    title: found.title,
    markdown: [`${'#'.repeat(level)} ${heading}`, text, ...nested].join('\n\n') + '\n',
    sections: anchors,
  };
}

module.exports = { loadDocs, searchDocs, readDoc, readSite };
