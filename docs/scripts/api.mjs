// Builds the API reference from the types of the package and their comments. For every section of `sections.json`
// it writes `.api/<section>.json` (what the components show) and `reference/<section>/*.mdx` (the pages). Every
// export of `src/index.ts` must belong to exactly one section; the script fails otherwise.
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const DOCS = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const REPO = path.resolve(DOCS, '..');
const ts = createRequire(path.join(REPO, 'package.json'))('typescript');
const SOURCE = 'https://github.com/apexnative/navigation/blob/main/';
const OUT_DATA = path.join(DOCS, '.api');
const OUT_PAGES = path.join(DOCS, 'reference');
// Versions at or below the oldest supported iOS are not worth a mark.
const MIN_IOS = 15.1;
const ALL = ['ios', 'ipad', 'android'];

const registry = JSON.parse(fs.readFileSync(path.join(DOCS, 'sections.json'), 'utf8'));
const sections = registry.groups.flatMap((group) =>
  group.sections.map((section) => ({ ...section, group: group.id, groupTitle: group.title }))
);
const apiSections = sections.filter((section) => !section.guide);

// ---------------------------------------------------------------------------------------------------- the program

const config = ts.getParsedCommandLineOfConfigFile(
  path.join(REPO, 'tsconfig.json'),
  {},
  {
    ...ts.sys,
    onUnRecoverableConfigFileDiagnostic: (d) => {
      throw new Error(ts.flattenDiagnosticMessageText(d.messageText, '\n'));
    },
  }
);
const program = ts.createProgram([path.join(REPO, 'src/index.ts')], config.options);
const checker = program.getTypeChecker();
const entryFile = program.getSourceFile(path.join(REPO, 'src/index.ts'));
const exported = new Map();
for (const symbol of checker.getExportsOfModule(checker.getSymbolAtLocation(entryFile))) {
  exported.set(
    symbol.name,
    symbol.flags & ts.SymbolFlags.Alias ? checker.getAliasedSymbol(symbol) : symbol
  );
}

// ------------------------------------------------------------------------------------------------- comments

// One line of text; the doc comments inside an inline type (`{ /** The id. */ id?: string }`) are left out.
const flat = (text) =>
  text
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\s+/g, ' ')
    .trim();

function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function inline(text) {
  return escapeHtml(text)
    .replace(
      /\{@link\s+([^}\s|]+)(?:\s*\|\s*([^}]+))?\}/g,
      (_, target, label) => `<code>${label ?? target}</code>`
    )
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2">$1</a>');
}

// A small subset of Markdown, enough for the comments: paragraphs, lists, fences, inline code, bold and links.
function markdown(text) {
  const blocks = [];
  const lines = text.replace(/\r/g, '').split('\n');
  let paragraph = [];
  let list = null;
  const flushParagraph = () => {
    if (paragraph.length) blocks.push(`<p>${inline(paragraph.join(' '))}</p>`);
    paragraph = [];
  };
  const flushList = () => {
    if (list) blocks.push(`<ul>${list.map((item) => `<li>${inline(item)}</li>`).join('')}</ul>`);
    list = null;
  };
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    const fence = line.match(/^\s*```(\w*)/);
    if (fence) {
      flushParagraph();
      flushList();
      const code = [];
      for (i += 1; i < lines.length && !/^\s*```/.test(lines[i]); i += 1) code.push(lines[i]);
      blocks.push(`<pre><code>${escapeHtml(dedent(code.join('\n')))}</code></pre>`);
      continue;
    }
    const item = line.match(/^\s*[-*]\s+(.*)$/);
    if (item) {
      flushParagraph();
      list = list ?? [];
      list.push(item[1]);
      continue;
    }
    if (!line.trim()) {
      flushParagraph();
      flushList();
      continue;
    }
    if (list && /^\s{2,}/.test(line)) {
      list[list.length - 1] += ' ' + line.trim();
      continue;
    }
    flushList();
    paragraph.push(line.trim());
  }
  flushParagraph();
  flushList();
  return blocks.join('');
}

function dedent(text) {
  const lines = text.split('\n');
  const indent = Math.min(...lines.filter((l) => l.trim()).map((l) => l.match(/^\s*/)[0].length));
  return lines.map((l) => l.slice(Number.isFinite(indent) ? indent : 0)).join('\n');
}

// Platform marks: the `@platform` and `@since` tags, or the older text marks — `#### (iOS specific)`,
// `(Android specific)`, `(iOS specific, iOS 16+)`, `(iOS 26+ specific)` — and a leading `(iOS)`.
function platformMarks(text, tags) {
  let platforms = null;
  let since = null;
  const take = (mark) => {
    const ios = /\biOS\b/.test(mark);
    const android = /\bAndroid\b/.test(mark);
    if (ios || android)
      platforms = [...(ios ? ['ios', 'ipad'] : []), ...(android ? ['android'] : [])];
    const version = mark.match(/iOS\s+(\d+(?:\.\d+)?)\+/);
    if (version && Number(version[1]) > MIN_IOS) since = `${version[1]}+`;
  };
  let rest = text.replace(/#{2,4}\s*\(([^)]*)\)/g, (_, mark) => {
    take(mark);
    return '';
  });
  rest = rest.replace(/^\s*\((iOS|Android)\)\s*/, (_, mark) => {
    take(mark);
    return '';
  });
  for (const tag of tags) {
    if (tag.name === 'platform') {
      const words = tag.text.toLowerCase().split(/[\s,]+/);
      platforms = [
        ...(words.includes('ios') ? ['ios'] : []),
        ...(words.includes('ios') || words.includes('ipados') ? ['ipad'] : []),
        ...(words.includes('android') ? ['android'] : []),
      ];
    }
    if (tag.name === 'since') take(tag.text);
  }
  return { text: rest.trim(), platforms, since };
}

function docOf(symbol) {
  const raw = ts.displayPartsToString(symbol.getDocumentationComment(checker));
  const tags = symbol.getJsDocTags(checker).map((tag) => ({
    name: tag.name,
    text: ts.displayPartsToString(tag.text ?? []).trim(),
  }));
  const marks = platformMarks(raw, tags);
  const tag = (name) => tags.find((t) => t.name === name);
  // A deprecated key often keeps its platform mark inside the deprecation text.
  const deprecated = tag('deprecated') ? platformMarks(tag('deprecated').text, []) : null;
  const summary = marks.text.split(/\n\s*\n/)[0] ?? '';
  return {
    html: markdown(marks.text),
    summary: flat(summary.replace(/`/g, '')),
    summaryHtml: inline(flat(summary)),
    platforms: marks.platforms ?? deprecated?.platforms ?? null,
    since: marks.since ?? deprecated?.since ?? null,
    default: tag('default')?.text || null,
    deprecated: deprecated ? markdown(deprecated.text || 'Deprecated.') : null,
    examples: tags
      .filter((t) => t.name === 'example')
      .map((t) => t.text.replace(/^```\w*\n?|```$/g, '').trim()),
    returns: tag('returns')?.text || tag('return')?.text || null,
  };
}

function where(node) {
  const file = node.getSourceFile();
  const line = file.getLineAndCharacterOfPosition(node.getStart()).line + 1;
  const relative = path.relative(REPO, file.fileName);
  return { file: relative, line, url: `${SOURCE}${relative}#L${line}` };
}

const inRepoSource = (decl) =>
  !!decl && decl.getSourceFile().fileName.startsWith(path.join(REPO, 'src') + path.sep);

// ------------------------------------------------------------------------------------------------- signatures

function headText(decl) {
  let text = decl.getText();
  if (decl.body) text = text.slice(0, decl.body.getStart() - decl.getStart());
  return dedent(
    text
      .replace(/^export\s+(default\s+)?/, '')
      .replace(/^declare\s+/, '')
      .replace(/;\s*$/, '')
      .trim()
  );
}

function paramsOf(decl) {
  return decl.parameters.map((p) => {
    const tags = ts.getJSDocParameterTags(p);
    const comment = tags.length ? (ts.getTextOfJSDocComment(tags[0].comment) ?? '') : '';
    return {
      name: (p.dotDotDotToken ? '...' : '') + p.name.getText(),
      type: flat(p.type ? p.type.getText() : checker.typeToString(checker.getTypeAtLocation(p))),
      optional: !!(p.questionToken || p.initializer),
      default: p.initializer ? p.initializer.getText() : null,
      html: comment ? inline(flat(comment)) : '',
    };
  });
}

function returnOf(decl) {
  if (decl.type) return flat(decl.type.getText());
  const signature = checker.getSignatureFromDeclaration(decl);
  return signature ? checker.typeToString(checker.getReturnTypeOfSignature(signature)) : 'void';
}

const isPrivate = (decl) =>
  (ts.getCombinedModifierFlags(decl) & (ts.ModifierFlags.Private | ts.ModifierFlags.Protected)) !==
  0;

// The properties of a type; a union or an intersection gives those of every part (a config that takes `root` or
// `roots` lists both).
function propertiesOf(type, found = new Map()) {
  if (type.isUnion() || type.isIntersection()) {
    for (const part of type.types) propertiesOf(part, found);
    return found;
  }
  for (const property of checker.getPropertiesOfType(type))
    if (!found.has(property.name)) found.set(property.name, property);
  return found;
}

function membersOf(type) {
  const out = [];
  for (const property of propertiesOf(type).values()) {
    if (property.name.startsWith('__')) continue;
    const decls = (property.declarations ?? []).filter((d) => !isPrivate(d) && inRepoSource(d));
    if (!decls.length) continue;
    const first = decls[0];
    const doc = docOf(property);
    const method = ts.isMethodSignature(first) || ts.isMethodDeclaration(first);
    const overloads = method && decls.length > 1 ? decls.filter((d) => !d.body) : decls;
    out.push({
      name: property.name,
      method,
      optional: !!first.questionToken,
      readonly: !!(ts.getCombinedModifierFlags(first) & ts.ModifierFlags.Readonly),
      signature: method
        ? overloads.map((d) => headText(d)).join('\n')
        : `${property.name}${first.questionToken ? '?' : ''}: ${flat(
            first.type
              ? first.type.getText()
              : checker.typeToString(checker.getTypeOfSymbolAtLocation(property, first))
          )}`,
      type: method
        ? null
        : flat(
            first.type
              ? first.type.getText()
              : checker.typeToString(checker.getTypeOfSymbolAtLocation(property, first))
          ),
      params: method ? paramsOf(overloads[0]) : [],
      returns: method ? returnOf(overloads[0]) : null,
      ...doc,
      platforms: doc.platforms ?? ALL,
      source: where(first),
    });
  }
  return out;
}

// --------------------------------------------------------------------------------------------------- entries

function describe(name, symbol) {
  const decls = symbol.declarations ?? [];
  const decl = symbol.valueDeclaration ?? decls[0];
  const doc = docOf(symbol);
  const base = { name, ...doc, platforms: doc.platforms ?? ALL, source: where(decl) };

  if (ts.isFunctionDeclaration(decl)) {
    const overloads = decls.filter((d) => ts.isFunctionDeclaration(d) && !d.body);
    const shown = overloads.length ? overloads : [decl];
    const returns = returnOf(shown[0]);
    const kind = name.startsWith('use')
      ? 'hook'
      : /Element$/.test(returns)
        ? 'builder'
        : 'function';
    return {
      ...base,
      kind,
      signature: shown.map((d) => headText(d)).join('\n'),
      params: paramsOf(shown[0]),
      returns,
    };
  }
  if (ts.isVariableDeclaration(decl)) {
    const type = checker.getTypeOfSymbolAtLocation(symbol, decl);
    const typeName = decl.type ? flat(decl.type.getText()) : checker.typeToString(type);
    return {
      ...base,
      kind: 'object',
      signature: `const ${name}: ${typeName}`,
      members: membersOf(type),
    };
  }
  if (ts.isClassDeclaration(decl)) {
    return {
      ...base,
      kind: 'class',
      signature: `class ${name}${decl.heritageClauses ? ' ' + decl.heritageClauses.map((h) => h.getText()).join(' ') : ''}`,
      members: membersOf(checker.getDeclaredTypeOfSymbol(symbol)).filter((m) => m.method),
    };
  }
  if (ts.isInterfaceDeclaration(decl)) {
    return {
      ...base,
      kind: 'interface',
      signature: `interface ${name}${decl.typeParameters ? `<${decl.typeParameters.map((p) => p.getText()).join(', ')}>` : ''}${
        decl.heritageClauses ? ' ' + decl.heritageClauses.map((h) => h.getText()).join(' ') : ''
      }`,
      members: membersOf(checker.getDeclaredTypeOfSymbol(symbol)),
    };
  }
  if (ts.isTypeAliasDeclaration(decl)) {
    const members = membersOf(checker.getDeclaredTypeOfSymbol(symbol));
    return {
      ...base,
      kind: 'type',
      signature: headText(decl),
      ...(members.length ? { members } : {}),
    };
  }
  if (ts.isEnumDeclaration(decl)) {
    return { ...base, kind: 'enum', signature: headText(decl) };
  }
  return { ...base, kind: 'other', signature: name };
}

// --------------------------------------------------------------------------------------------------- options

const claims = new Map();
for (const section of apiSections)
  for (const option of section.options ?? []) claims.set(option, section);

const optionsType = checker.getDeclaredTypeOfSymbol(exported.get('Options'));

const namedInterface = (type) => {
  const decl = type.getSymbol()?.declarations?.[0];
  return decl && ts.isInterfaceDeclaration(decl) ? type.getSymbol().name : null;
};

// How many keys of the options have each named interface as their type. One that more keys share (the button of
// the bar, the config of an animated property) is not unfolded under each of them: the key links to the type.
const sharedTypes = (() => {
  const uses = new Map();
  const visited = new Set();
  const visit = (type) => {
    if (visited.has(type.id)) return;
    visited.add(type.id);
    for (const property of checker.getPropertiesOfType(type)) {
      const decl = property.valueDeclaration ?? property.declarations?.[0];
      if (!decl) continue;
      for (const part of objectParts(
        checker.getNonNullableType(checker.getTypeOfSymbolAtLocation(property, decl))
      )) {
        const name = namedInterface(part);
        if (name) uses.set(name, (uses.get(name) ?? 0) + 1);
        visit(part);
      }
    }
  };
  visit(optionsType);
  return new Set([...uses].filter(([, count]) => count > 1).map(([name]) => name));
})();
const unfoldedTypes = new Set();

function objectParts(type) {
  const parts = type.isUnion() ? type.types : [type];
  const out = [];
  for (let part of parts) {
    if (checker.isArrayType(part)) part = checker.getTypeArguments(part)[0];
    if (!part || !(part.flags & ts.TypeFlags.Object)) continue;
    if (part.getCallSignatures().length) continue;
    const decl = part.getSymbol()?.declarations?.[0];
    if (!inRepoSource(decl)) continue;
    out.push(part);
  }
  return out;
}

function optionNode(property, at, inherited, section, ancestors) {
  const decl = property.valueDeclaration ?? property.declarations[0];
  const doc = docOf(property);
  const type = checker.getNonNullableType(checker.getTypeOfSymbolAtLocation(property, decl));
  const claim = claims.get(at);
  const node = {
    name: property.name,
    path: at,
    required: !(property.flags & ts.SymbolFlags.Optional),
    type: flat(decl.type ? decl.type.getText() : checker.typeToString(type)),
    html: doc.html,
    summary: doc.summary,
    default: doc.default,
    deprecated: doc.deprecated,
    platforms: doc.platforms ?? inherited,
    since: doc.since,
    source: where(decl),
    link: claim && claim.id !== section.id ? { id: claim.id, title: claim.title } : null,
    kids: [],
  };
  if (node.link) return node;
  const parts = objectParts(type).filter((part) => !ancestors.includes(part.id));
  const shared = parts.map(namedInterface).filter((name) => name && sharedTypes.has(name));
  // Only exported types have a page to link to; a shared inner one (the theme color of `Color`) stays folded.
  const linked = shared.filter((name) => owner.has(name));
  if (linked.length) node.typeLinks = linked.map((name) => ({ name, section: owner.get(name) }));
  const seen = new Set();
  for (const part of parts) {
    const name = namedInterface(part);
    if (name && sharedTypes.has(name)) continue;
    if (name) unfoldedTypes.add(name);
    for (const child of checker.getPropertiesOfType(part)) {
      if (seen.has(child.name)) continue;
      seen.add(child.name);
      node.kids.push(
        optionNode(child, `${at}.${child.name}`, node.platforms, section, [...ancestors, part.id])
      );
    }
  }
  return node;
}

function optionAt(at, section) {
  const names = at.split('.');
  let type = optionsType;
  let property;
  const trail = [];
  for (const name of names) {
    property = type.getProperty(name);
    if (!property) throw new Error(`sections.json: no option ${at} (section ${section.id})`);
    const decl = property.valueDeclaration ?? property.declarations[0];
    trail.push(docOf(property).platforms);
    type =
      objectParts(
        checker.getNonNullableType(checker.getTypeOfSymbolAtLocation(property, decl))
      )[0] ?? type;
  }
  const inherited = trail.slice(0, -1).reverse().find(Boolean) ?? ALL;
  return optionNode(property, at, inherited, section, []);
}

// -------------------------------------------------------------------------------------------- Showcase links

function scenarios() {
  const root = path.join(REPO, 'example/src/sections');
  const out = [];
  for (const dir of fs.readdirSync(root)) {
    const full = path.join(root, dir);
    // Hidden folders (tool state such as `.claude/`) hold no scenarios.
    if (dir.startsWith('.') || !fs.statSync(full).isDirectory()) continue;
    for (const file of fs.readdirSync(full)) {
      if (!fs.statSync(path.join(full, file)).isFile()) continue;
      const text = fs.readFileSync(path.join(full, file), 'utf8');
      const id = text.match(/\bid:\s*'([\d.]+)'/)?.[1];
      if (!id) continue;
      const title = text.match(/\btitle:\s*(['"])(.*?)\1/)?.[2] ?? id;
      const keywords = [
        ...(text.match(/\bkeywords:\s*\[([^\]]*)\]/)?.[1] ?? '').matchAll(/'([^']+)'/g),
      ].map((m) => m[1]);
      out.push({
        id,
        title,
        keywords,
        url: `${SOURCE}${path.relative(REPO, path.join(full, file))}`,
      });
    }
  }
  return out.sort((a, b) => a.id.localeCompare(b.id, 'en', { numeric: true }));
}
const allScenarios = scenarios();

const scenariosOf = (section) =>
  allScenarios.filter((s) =>
    (section.showcase ?? []).some((id) => s.id === id || s.id.startsWith(id + '.'))
  );

function linkScenarios(node, pool) {
  const exact = pool.filter((s) =>
    s.keywords.some((k) => k.includes('.') && node.path.endsWith(k))
  );
  const byName = pool.filter((s) => s.keywords.includes(node.name));
  node.showcase = (exact.length ? exact : byName).map(({ id, url }) => ({ id, url }));
  const owner = node.link ? apiSections.find((s) => s.id === node.link.id) : null;
  for (const kid of node.kids) linkScenarios(kid, owner ? scenariosOf(owner) : pool);
}

// ------------------------------------------------------------------------------------------------- the pages

function frontmatter(fields) {
  return `---\n${Object.entries(fields)
    .filter(([, value]) => value !== undefined && value !== null)
    .map(
      ([key, value]) =>
        `${key}: ${typeof value === 'string' || Array.isArray(value) ? JSON.stringify(value) : value}`
    )
    .join('\n')}\n---\n`;
}

// Hand-written parts of a reference page: `extras/<section>/<page>.<slot>.mdx`, put in as they are. Slots: `intro`
// (after the header), `body` (before what the page returns), `returns` (under the heading of what it returns),
// `outro` (at the end). Their imports go to the top of the page.
function extras(section, page) {
  const out = { imports: [], intro: '', body: '', returns: '', outro: '' };
  for (const slot of ['intro', 'body', 'returns', 'outro']) {
    const file = path.join(DOCS, 'extras', section.id, `${page}.${slot}.mdx`);
    if (!fs.existsSync(file)) continue;
    // Imports of the page, not the ones in its code blocks.
    let fenced = false;
    const body = [];
    for (const line of fs.readFileSync(file, 'utf8').split('\n')) {
      if (/^\s*```/.test(line)) fenced = !fenced;
      if (!fenced && line.startsWith('import ')) out.imports.push(line);
      else body.push(line);
    }
    out[slot] = body.join('\n').trim() + '\n';
  }
  return out;
}

const listIds = (ids) =>
  ids.length > 1 ? `${ids.slice(0, -1).join(', ')} and ${ids[ids.length - 1]}` : ids[0];

// The line of the Showcase card in "On this page".
function showcaseNote(section, kind) {
  const ids = section.showcase ?? [];
  if (!ids.length) return undefined;
  if (kind === 'options' && ids.every((id) => !id.includes('.'))) {
    return `Section ${listIds(ids)} shows every key of the ${section.title.toLowerCase()} on iOS and Android.`;
  }
  const what =
    {
      hook: 'this hook',
      builder: 'this builder',
      object: 'these commands',
      options: 'these options',
    }[kind] ?? 'it';
  return `${ids.length > 1 ? 'Scenarios' : 'Scenario'} ${listIds(ids)} run ${what} on iOS and Android.`;
}

function exportPage(section, entry, position) {
  const extra = extras(section, entry.name);
  const membersOf = entry.returnsMembers ?? (entry.members?.length ? entry.name : null);
  const membersEntry = membersOf ? entries.get(membersOf) : null;
  const membersHeading = entry.returnsMembers
    ? `Returns ${entry.returnsMembers}`
    : entry.kind === 'class'
      ? 'Methods'
      : 'Members';
  const lines = [
    frontmatter({
      id: entry.name,
      // An export named as its folder (Stack in stack/) would otherwise become the index of the folder.
      slug: `/${section.id}/${entry.name}`,
      title: entry.name,
      sidebar_label: entry.name,
      sidebar_position: position,
      hide_title: true,
      description: entry.summary || `${entry.name} of @apexnative/navigation.`,
      showcase_note: showcaseNote(section, entry.kind),
      toc_members: membersEntry?.members?.length
        ? membersEntry.members.map((m) => m.name)
        : undefined,
      toc_members_after: membersEntry?.members?.length ? 'returns' : undefined,
    }),
    `import data from '@site/.api/${section.id}.json';`,
    `import { ApiHeader, ApiSignature, ApiParams, ApiExamples, ApiMembers, ApiFooter } from '@site/src/components/Api';`,
    ...extra.imports,
    '',
    `<ApiHeader data={data} name="${entry.name}" />`,
    '',
    extra.intro,
    '## Signature {#signature}',
    '',
    `<ApiSignature data={data} name="${entry.name}" />`,
    '',
  ];
  if (entry.params?.length)
    lines.push(
      '## Parameters {#parameters}',
      '',
      `<ApiParams data={data} name="${entry.name}" />`,
      ''
    );
  if (entry.examples?.length)
    lines.push('## Example {#example}', '', `<ApiExamples data={data} name="${entry.name}" />`, '');
  lines.push(extra.body);
  if (membersOf) {
    lines.push(
      `## ${membersHeading} {#returns}`,
      '',
      extra.returns,
      `<ApiMembers data={data} name="${membersOf}" />`,
      ''
    );
  }
  lines.push(extra.outro, `<ApiFooter url="${entry.source.url}" />`, '');
  return lines.join('\n');
}

// The members a written page must have a heading for: what the export takes, what it returns, its own members.
function requiredMembers(entry) {
  const out = [];
  for (const of of [entry.paramMembers, entry.returnsMembers]) {
    if (!of) continue;
    for (const member of entries.get(of).members) out.push({ of, name: member.name });
  }
  if (['object', 'class'].includes(entry.kind))
    for (const member of entry.members ?? []) out.push({ of: entry.name, name: member.name });
  return out;
}

// A reference page written by hand: `api/<section>/<export>.mdx`. It explains the export the way a reader learns it
// (what it is for, an example, then a heading for every member it takes or returns, with `<ApiMember>` for the facts);
// the page gets the header, the data and the components here. A member without its heading `{#name}` fails the run.
function writtenPage(section, entry, position) {
  const file = path.join(DOCS, 'api', section.id, `${entry.name}.mdx`);
  if (!fs.existsSync(file)) return null;
  const text = fs.readFileSync(file, 'utf8');
  const anchors = new Set([...text.matchAll(/\{#([\w-]+)\}/g)].map((m) => m[1]));
  for (const { of, name } of requiredMembers(entry))
    if (!anchors.has(name))
      uncovered.push(
        `api/${section.id}/${entry.name}.mdx: ${of}.${name} has no heading {#${name}}`
      );
  let fenced = false;
  const imports = [];
  const body = [];
  for (const line of text.split('\n')) {
    if (/^\s*```/.test(line)) fenced = !fenced;
    if (!fenced && line.startsWith('import ')) imports.push(line);
    else body.push(line);
  }
  return [
    frontmatter({
      id: entry.name,
      slug: `/${section.id}/${entry.name}`,
      title: entry.name,
      sidebar_label: entry.name,
      sidebar_position: position,
      hide_title: true,
      description: entry.summary || `${entry.name} of @apexnative/navigation.`,
      showcase_note: showcaseNote(section, entry.kind),
    }),
    `import data from '@site/.api/${section.id}.json';`,
    `import { ApiHeader, ApiSignature, ApiMember, ApiFooter } from '@site/src/components/Api';`,
    ...imports,
    '',
    `<ApiHeader data={data} name="${entry.name}" />`,
    '',
    body.join('\n').trim(),
    '',
    `<ApiFooter url="${entry.source.url}" />`,
    '',
  ].join('\n');
}

function optionsPage(section, roots, position) {
  const extra = extras(section, 'options');
  const single = roots.length === 1 && roots[0].kids.length > 0;
  const title = single ? roots[0].path : 'Options';
  // The keys at the top of the tree go into "On this page", under Options, each linking to its row; a key another
  // section describes, and a deprecated one (the tree hides it), stay out.
  const top = (single ? roots[0].kids : roots).filter((node) => !node.link && !node.deprecated);
  return [
    frontmatter({
      id: 'options',
      title,
      sidebar_label: single ? roots[0].path : 'Options',
      sidebar_position: position,
      hide_title: true,
      description: `The options of ${section.title.toLowerCase()}.`,
      showcase_note: showcaseNote(section, 'options'),
      toc_members: top.map((node) => (single ? node.name : node.path)),
      toc_members_ids: top.map((node) => `option-${node.path}`),
      toc_members_after: 'options',
    }),
    `import data from '@site/.api/${section.id}.json';`,
    `import { OptionsHeader, OptionsTree, ApiFooter } from '@site/src/components/Api';`,
    ...extra.imports,
    '',
    `<OptionsHeader data={data} />`,
    '',
    extra.intro,
    '## Options {#options}',
    '',
    'Every key, with the platforms it works on. A dashed platform means the key does nothing there.',
    '',
    `<OptionsTree data={data} />`,
    '',
    extra.outro,
    `<ApiFooter url="${roots[0].source.url}" />`,
    '',
  ].join('\n');
}

function typesPage(section, names, position) {
  return [
    frontmatter({
      id: 'types',
      title: `Types of ${section.title.toLowerCase()}`,
      sidebar_label: 'Types',
      sidebar_position: position,
      hide_title: true,
      description: `The types of ${section.title.toLowerCase()}.`,
      showcase_note: showcaseNote(section, 'types'),
    }),
    `import data from '@site/.api/${section.id}.json';`,
    `import { TypesHeader, ApiType } from '@site/src/components/Api';`,
    '',
    `<TypesHeader data={data} />`,
    '',
    ...names.flatMap((name) => [
      `## ${name} {#${name.toLowerCase()}}`,
      '',
      `<ApiType data={data} name="${name}" />`,
      '',
    ]),
  ].join('\n');
}

// --------------------------------------------------------------------------------------------------- the run

const owner = new Map();
const problems = [];
for (const section of apiSections) {
  for (const name of [...(section.pages ?? []), ...(section.types ?? [])]) {
    if (!exported.has(name))
      problems.push(`${section.id}: ${name} is not exported from src/index.ts`);
    else if (owner.has(name)) problems.push(`${name} is in ${owner.get(name)} and ${section.id}`);
    else owner.set(name, section.id);
  }
}
for (const name of exported.keys())
  if (!owner.has(name)) problems.push(`${name}: in no section of sections.json`);
if (problems.length) {
  console.error(`[api] sections.json does not match the exports:\n  ${problems.join('\n  ')}`);
  process.exit(1);
}

fs.rmSync(OUT_DATA, { recursive: true, force: true });
fs.rmSync(OUT_PAGES, { recursive: true, force: true });
fs.mkdirSync(OUT_DATA, { recursive: true });

const entries = new Map([...exported].map(([name, symbol]) => [name, describe(name, symbol)]));

// A function's page shows the members of what it returns (`Tabs` → `TabsElement`) and of the object it takes
// (`createNavigation` → `NavigationConfig`), when that type is an export with members.
const withMembers = (typeText) => {
  const name = typeText?.replace(/<[\s\S]*$/, '').trim();
  return name && entries.get(name)?.members?.length ? name : null;
};
for (const entry of entries.values()) {
  if (!['function', 'hook', 'builder'].includes(entry.kind)) continue;
  entry.returnsMembers = withMembers(entry.returns);
  const takes = (entry.params ?? []).map((p) => withMembers(p.type)).filter(Boolean);
  entry.paramMembers = takes.length === 1 ? takes[0] : null;
}
const sectionOf = (name) => apiSections.find((s) => s.id === owner.get(name));

let pageCount = 0;
// Reference pages still made from the JSDoc alone, and members a written page leaves out.
const unwritten = [];
const uncovered = [];
for (const section of apiSections) {
  const pool = scenariosOf(section);
  const roots = (section.options ?? []).map((at) => optionAt(at, section));
  for (const root of roots) linkScenarios(root, pool);

  const pages = section.pages ?? [];
  const types = section.types ?? [];
  // Members shown on a page may come from a type of another section (`Stack` returns `StackElement`).
  const shown = new Set([...pages, ...types]);
  for (const name of pages) {
    const { returnsMembers, paramMembers } = entries.get(name);
    if (returnsMembers) shown.add(returnsMembers);
    if (paramMembers) shown.add(paramMembers);
  }
  const data = {
    section: {
      id: section.id,
      title: section.title,
      group: section.groupTitle,
      status: section.status ?? 'draft',
      memberGroups: section.memberGroups ?? {},
      showcase: pool.map(({ id, title, url }) => ({ id, title, url })),
    },
    entries: Object.fromEntries(
      [...shown].map((name) => [name, { ...entries.get(name), section: sectionOf(name)?.id }])
    ),
    options: roots,
    sections: Object.fromEntries(apiSections.map((s) => [s.id, s.title])),
  };
  fs.writeFileSync(path.join(OUT_DATA, `${section.id}.json`), JSON.stringify(data));

  const dir = path.join(OUT_PAGES, section.id);
  fs.mkdirSync(dir, { recursive: true });
  let position = 1;
  for (const name of pages) {
    const written = writtenPage(section, entries.get(name), position);
    fs.writeFileSync(
      path.join(dir, `${name}.mdx`),
      written ?? exportPage(section, entries.get(name), position)
    );
    position += 1;
    pageCount += 1;
    if (!written) unwritten.push(`${section.id}/${name}`);
  }
  if (roots.length) {
    fs.writeFileSync(path.join(dir, 'options.mdx'), optionsPage(section, roots, position++));
    pageCount += 1;
  }
  const typeNames = types.filter((name) => !isOptionType(name));
  if (typeNames.length) {
    fs.writeFileSync(path.join(dir, 'types.mdx'), typesPage(section, typeNames, position++));
    pageCount += 1;
  }
}

// The interfaces unfolded in an options tree show there; the types page leaves them out.
function isOptionType(name) {
  return unfoldedTypes.has(name);
}

fs.writeFileSync(
  path.join(OUT_DATA, 'sidebar.json'),
  JSON.stringify(
    registry.groups
      .filter((group) => group.sections.some((s) => !s.guide))
      .map((group) => ({
        id: group.id,
        title: group.title,
        sections: group.sections
          .filter((s) => !s.guide)
          .map((s) => ({
            id: s.id,
            title: s.title,
            status: s.status ?? 'draft',
            docs: pagesOf(s).map((id) => `${s.id}/${id}`),
            labels: pagesOf(s).map((id) => labelOf(s, id)),
          })),
      }))
  )
);

function pagesOf(section) {
  return fs
    .readdirSync(path.join(OUT_PAGES, section.id))
    .map((file) => file.replace(/\.mdx$/, ''))
    .sort((a, b) => order(section, a) - order(section, b));
}

// The label of a page in the sidebar: its export, the options root, or "Types".
function labelOf(section, id) {
  if (id === 'types') return 'Types';
  if (id !== 'options') return id;
  const roots = section.options ?? [];
  return roots.length === 1 ? roots[0] : 'Options';
}

function order(section, id) {
  const pages = section.pages ?? [];
  if (pages.includes(id)) return pages.indexOf(id);
  return id === 'options' ? pages.length : pages.length + 1;
}

// Exports with an empty JSDoc: their reference page has only a signature. The JSDoc is written section by section;
// until none is left they are listed here, and scripts/docs.sh prints how many.
const undocumented = [...entries].filter(([, entry]) => !entry.summary).map(([name]) => name);
if (undocumented.length) {
  console.log(`[api] without JSDoc: ${undocumented.length}`);
  for (const section of apiSections) {
    const names = undocumented.filter((name) => owner.get(name) === section.id);
    if (names.length) console.log(`  ${section.id}: ${names.join(', ')}`);
  }
}

if (unwritten.length) {
  console.log(`[api] reference pages not written yet: ${unwritten.length}`);
  console.log(`  ${unwritten.join(', ')}`);
}
if (uncovered.length) {
  console.error(
    `[api] members without their section on a written page:\n  ${uncovered.join('\n  ')}`
  );
  process.exit(1);
}

console.log(`[api] ${exported.size} exports, ${apiSections.length} sections, ${pageCount} pages`);
