// @ts-check
'use strict';

/**
 * A page of the docs as plain Markdown, for an agent: its title, description, text, and its sections by anchor.
 * @typedef {{
 *   id: string;
 *   title: string;
 *   description: string;
 *   markdown: string;
 *   anchors: { heading: string; anchor: string }[];
 *   sections: { heading: string; anchor: string; level: number; text: string }[];
 * }} DocPage
 */

/**
 * A page of the site (MDX) as plain Markdown: the components of the site go, keeping what they say (the steps of
 * `Steps`, the label of a tab, a marked phrase in bold, the platform of `Os`), the notes become quotes, and the code
 * stays as it is.
 * @param {string} id the page, `group/section`
 * @param {string} mdx
 * @returns {DocPage}
 */
function toMarkdown(id, mdx) {
  const front = /^---\n([\s\S]*?)\n---\n/.exec(mdx);
  const meta = front?.[1] ?? '';
  const title = unquote(/^title:\s*(.+)$/m.exec(meta)?.[1] ?? id);
  const description = unquote(/^description:\s*(.+)$/m.exec(meta)?.[1] ?? '');
  const lines = mdx.slice(front ? front[0].length : 0).split('\n');

  /** @type {string[]} */
  const out = [`# ${title}`, '', ...(description ? [description, ''] : [])];
  let fence = '';
  let note = false;
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    // Code stays as it is: its JSX is the app's, not the site's.
    const fenceMark = /^\s*(```+|~~~+)/.exec(line)?.[1];
    if (fence) {
      out.push(line);
      if (fenceMark && fenceMark.startsWith(fence)) fence = '';
      continue;
    }
    if (fenceMark) {
      fence = fenceMark;
      out.push(line.replace(/\s+title="[^"]*"/, ''));
      continue;
    }
    if (/^import\s/.test(line)) {
      while (index < lines.length && !/from\s+['"][^'"]+['"];?\s*$/.test(lines[index])) index++;
      continue;
    }
    const admonition = /^:::(\w+)?/.exec(line.trim());
    if (admonition) {
      note = Boolean(admonition[1]);
      if (note) out.push(`> **${capitalize(admonition[1])}**`, '>');
      continue;
    }
    // A component of the site on lines of its own: what it says, or nothing. One followed by text on its line, such as
    // `<Os name="iOS" /> 16+ …`, is part of the prose.
    const end = /^\s*<\/?[A-Z]/.test(line) ? tagEnd(line) : -2;
    if (end === -1 || (end >= 0 && line.slice(end).trim() === '')) {
      let tag = line;
      while (tagEnd(tag) === -1 && index + 1 < lines.length) tag += '\n' + lines[++index];
      out.push(...component(tag));
      continue;
    }
    const text = inline(line);
    out.push(note ? `> ${text}`.trimEnd() : text);
  }
  const markdown =
    out
      .join('\n')
      .replace(/\n{3,}/g, '\n\n')
      .trim() + '\n';
  const anchors = [...mdx.matchAll(/^#{2,4}\s+(.+?)\s*\{#([\w-]+)\}\s*$/gm)].map((found) => ({
    heading: inline(found[1]).trim(),
    anchor: found[2],
  }));
  return { id, title, description, markdown, anchors, sections: sectionsOf(markdown, anchors) };
}

/**
 * The sections of a page by its headings, each with the anchor the site gives it (`{#anchor}`, else from its words).
 * @param {string} markdown
 * @param {{ heading: string; anchor: string }[]} anchors
 * @returns {DocPage['sections']}
 */
function sectionsOf(markdown, anchors) {
  const headings = [...markdown.matchAll(/^(#{2,4})\s+(.+)$/gm)];
  return headings.map((found, index) => {
    const heading = found[2].trim();
    const start = /** @type {number} */ (found.index) + found[0].length;
    const end = headings[index + 1]?.index ?? markdown.length;
    return {
      heading,
      anchor:
        anchors.find((item) => item.heading === heading)?.anchor ??
        heading
          .toLowerCase()
          .replace(/[^\w]+/g, '-')
          .replace(/^-|-$/g, ''),
      level: found[1].length,
      text: markdown.slice(start, end).trim(),
    };
  });
}

/**
 * What a component of the site says, as Markdown lines: its items in order (a `title` or an `eyebrow` starts one,
 * `text`, `lines` and `description` go on with it, `code` in backticks), numbered for `Steps`.
 * @param {string} tag
 * @returns {string[]}
 */
function component(tag) {
  const name = /^\s*<\/?([A-Za-z]+)/.exec(tag)?.[1] ?? '';
  if (tag.trim().startsWith('</')) return [];
  if (name === 'TabItem') {
    const label = /label="([^"]+)"/.exec(tag)?.[1];
    return label ? ['', `**${label}**`, ''] : [];
  }
  if (name === 'PackageInstall') {
    const packages = /packages?=["{]['"]?([^"'}]+)/.exec(tag)?.[1];
    return packages ? ['```bash', `npm install ${packages.trim()}`, '```'] : [];
  }
  /** @type {string[]} */
  const items = [];
  let key = '';
  let fresh = false;
  const token = /(\w+)\s*:|(['"`])((?:\\.|(?!\2).)*)\2|<>([\s\S]*?)<\/>/g;
  for (const found of tag.matchAll(token)) {
    if (found[1]) {
      key = found[1];
      fresh = true;
      continue;
    }
    const value = (found[3] ?? inline(found[4] ?? '').trim()).replace(/\\(.)/g, '$1');
    if (key === 'title' || key === 'eyebrow') {
      const last = items.length - 1;
      // An eyebrow and then its title make one item.
      if (key === 'title' && last >= 0 && items[last].endsWith(' —'))
        items[last] += ` **${value}**`;
      else items.push(key === 'title' ? `**${value}**` : `${value} —`);
    } else if (key === 'code') {
      if (items.length > 0) items[items.length - 1] += ` \`${value}\``;
    } else if (key === 'text' || key === 'lines' || key === 'description') {
      const last = items.length - 1;
      if (last < 0 || (key === 'text' && fresh && !/\*\*$|—$/.test(items[last]))) items.push(value);
      else items[last] += /\*\*$/.test(items[last]) ? `: ${value}` : ` ${value}`;
    }
    fresh = false;
  }
  if (items.length === 0) return [];
  return [
    '',
    ...items.map((item, index) => (name === 'Steps' ? `${index + 1}. ${item}` : `- ${item}`)),
    '',
  ];
}

/**
 * Where the first tag of a line ends, just after its `>`; -1 when it doesn't end there, as a component written over
 * several lines. Braces and quotes inside it are skipped.
 * @param {string} text
 */
function tagEnd(text) {
  let depth = 0;
  /** @type {string | undefined} */
  let quote;
  const start = text.indexOf('<');
  for (let index = start + 1; index < text.length; index++) {
    const character = text[index];
    if (quote) {
      if (character === '\\') index++;
      else if (character === quote) quote = undefined;
      continue;
    }
    if (character === '"' || character === "'" || character === '`') quote = character;
    else if (character === '{' || character === '[' || character === '(') depth++;
    else if (character === '}' || character === ']' || character === ')') depth--;
    else if (character === '>' && depth === 0 && text[index - 1] !== '=') return index + 1;
  }
  return -1;
}

/**
 * A line of prose with the inline components of the site as text, and the heading anchors gone.
 * @param {string} line
 */
function inline(line) {
  return line
    .replace(/\s*\{#[\w-]+\}\s*$/, '')
    .replace(/<Mark>([\s\S]*?)<\/Mark>/g, '**$1**')
    .replace(/<Os\s+name="([^"]+)"\s*\/>/g, '($1)')
    .replace(/<[A-Z][A-Za-z]*\b[^>]*\/>/g, '')
    .replace(/<\/?[A-Z][A-Za-z]*\b[^>]*>/g, '');
}

/** @param {string} text */
function unquote(text) {
  const trimmed = text.trim();
  return /^(['"]).*\1$/.test(trimmed) ? trimmed.slice(1, -1).replace(/''/g, "'") : trimmed;
}

/** @param {string} text */
function capitalize(text) {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

module.exports = { toMarkdown, sectionsOf };
