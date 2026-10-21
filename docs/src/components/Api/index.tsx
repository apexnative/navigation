import Link from '@docusaurus/Link';
import useBrokenLinks from '@docusaurus/useBrokenLinks';
import { usePrismTheme } from '@docusaurus/theme-common';
import CodeBlock from '@theme/CodeBlock';
import DocItemTOCMobile from '@theme/DocItem/TOC/Mobile';
import clsx from 'clsx';
import { Highlight } from 'prism-react-renderer';
import React, { useMemo, useState } from 'react';
import sidebar from '@site/.api/sidebar.json';
import registry from '@site/sections.json';
import styles from './styles.module.css';

// The reference pages of the DocsApi and DocsOptions boards, element for element.

// ------------------------------------------------------------------------------------------------- data shapes

type Platform = 'ios' | 'ipad' | 'android';

interface Source {
  file: string;
  line: number;
  url: string;
}

interface Param {
  name: string;
  type: string;
  optional: boolean;
  default: string | null;
  html: string;
}

interface Doc {
  html: string;
  summary: string;
  summaryHtml?: string;
  platforms: Platform[];
  since: string | null;
  default: string | null;
  deprecated: string | null;
  examples?: string[];
  returns?: string | null;
  source: Source;
}

interface Member extends Omit<Doc, 'returns'> {
  name: string;
  method: boolean;
  optional: boolean;
  readonly: boolean;
  signature: string;
  type: string | null;
  params: Param[];
  returns: string | null;
  returnsNote?: string | null;
}

interface Entry extends Omit<Doc, 'returns'> {
  name: string;
  kind:
    'hook' | 'builder' | 'function' | 'object' | 'class' | 'interface' | 'type' | 'enum' | 'other';
  signature: string;
  params?: Param[];
  returns?: string | null;
  returnsMembers?: string | null;
  members?: Member[];
  section?: string;
}

interface OptionNode extends Omit<Doc, 'examples' | 'summaryHtml' | 'returns'> {
  name: string;
  path: string;
  /** The key has no `?`: the object it is in needs it. */
  required?: boolean;
  type: string;
  link: { id: string; title: string } | null;
  typeLinks?: { name: string; section: string }[];
  showcase?: { id: string; url: string }[];
  kids: OptionNode[];
}

type Status = 'draft' | 'review' | 'stable';

interface SectionData {
  section: {
    id: string;
    title: string;
    group: string;
    status: Status;
    memberGroups: Record<string, string>;
    showcase: { id: string; title: string; url: string }[];
  };
  entries: Record<string, Entry>;
  options: OptionNode[];
  sections: Record<string, string>;
}

const KIND: Record<Entry['kind'], string> = {
  hook: 'Hook',
  builder: 'Builder',
  function: 'Function',
  object: 'Object',
  class: 'Class',
  interface: 'Interface',
  type: 'Type',
  enum: 'Enum',
  other: 'Export',
};

const PLATFORMS: { id: Platform; label: string }[] = [
  { id: 'ios', label: 'iOS' },
  { id: 'ipad', label: 'iPadOS' },
  { id: 'android', label: 'Android' },
];

const html = (text: string) => ({ __html: text });

type RegistrySection = {
  id: string;
  title: string;
  status?: Status;
};
const allSections = registry.groups.flatMap((g) =>
  (g.sections as RegistrySection[]).map((s) => ({ ...s, group: g.id }))
);

const docsPath = (sectionId: string) => {
  const found = allSections.find((s) => s.id === sectionId);
  return `/docs/${found?.group ?? 'fundamentals'}/${sectionId}`;
};

const apiPath = (sectionId: string, doc?: string) => {
  const found = sidebar.flatMap((g) => g.sections).find((s) => s.id === sectionId);
  return `/api/${doc ? `${sectionId}/${doc}` : (found?.docs[0] ?? sectionId)}`;
};

function Chevron({ open, size = 14 }: { open: boolean; size?: number }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 12 12"
      className={styles.chevron}
      style={{ width: size, height: size, transform: `rotate(${open ? 90 : 0}deg)` }}
    >
      <path d="M4 2.5 L7.5 6 L4 9.5" />
    </svg>
  );
}

// --------------------------------------------------------------------------------------------- the platforms

/** A key, a field or a parameter that must be given. */
function Required() {
  return <span className={styles.req}>Required</span>;
}

export function Platforms({ platforms, since }: { platforms: Platform[]; since?: string | null }) {
  return (
    <span className={styles.pfs}>
      {PLATFORMS.map(({ id, label }) => {
        const on = platforms.includes(id);
        return (
          <span key={id} className={clsx(styles.pf, on ? styles[`pf_${id}`] : styles.pfOff)}>
            {label}
            {on && since && id !== 'android' ? ` ${since}` : ''}
            {!on && <span className="sr-only"> (not supported)</span>}
          </span>
        );
      })}
    </span>
  );
}

// ------------------------------------------------------------------------------------------ the section status

const STATUS_LABEL: Record<Status, string> = {
  stable: 'Stable',
  review: 'In review',
  draft: 'Draft',
};
const STATUS_NOTE: Record<Status, string> = {
  stable: 'Works as documented on every platform.',
  review: 'The API is being reviewed and can still change.',
  draft: 'Documented, not reviewed yet: the API can change.',
};

// A section that is done shows no card: the card marks what can still change.
function StatusCard({ id, title, status }: { id: string; title: string; status: Status }) {
  if (status === 'stable') return null;
  return (
    <section className={styles.stc} aria-label="Section status">
      <div className={styles.stcHead}>
        <span className="eyebrow" style={{ color: 'var(--an-muted)' }}>
          Section
        </span>
        <Link to={docsPath(id)} className={styles.stcTitle}>
          {title}
        </Link>
        <span className={clsx(styles.pill, styles[`pill_${status}`])}>{STATUS_LABEL[status]}</span>
        <span className={styles.stcNote}>{STATUS_NOTE[status]}</span>
      </div>
    </section>
  );
}

/** The status card of a section, for its page in the Docs. */
export function SectionStatus({ id }: { id: string }) {
  const section = allSections.find((s) => s.id === id);
  if (!section) return null;
  return <StatusCard id={id} title={section.title} status={section.status ?? 'draft'} />;
}

/** The API pages of a section, for its page in the Docs. */
export function SectionApi({ id }: { id: string }) {
  const section = sidebar.flatMap((g) => g.sections).find((s) => s.id === id);
  if (!section) return null;
  return (
    <div className={styles.alsoGrid}>
      {section.docs.map((doc) => {
        const name = doc.split('/')[1];
        return (
          <Link key={doc} to={`/api/${doc}`} className={styles.also}>
            <span className={styles.alsoName}>
              {name === 'options' ? 'Options' : name === 'types' ? 'Types' : name}
            </span>
          </Link>
        );
      })}
    </div>
  );
}

// -------------------------------------------------------------------------------------------- the page headers

function Header({
  data,
  kind,
  title,
  lead,
  importName,
  deprecated,
}: {
  data: SectionData;
  kind: string;
  title: string;
  lead?: string;
  importName?: string;
  deprecated?: string | null;
}) {
  const { section } = data;
  return (
    <header>
      <Link className="crumbs-back" to={docsPath(section.id)}>
        ‹ {section.title}
      </Link>
      <nav aria-label="Breadcrumbs" className="crumbs crumbs--api">
        <Link className="crumb" to={apiPath('starting-the-app')}>
          API
        </Link>
        <span className="crumbs__sep" aria-hidden="true">
          ›
        </span>
        <span className="crumb">{section.group}</span>
        <span className="crumbs__sep" aria-hidden="true">
          ›
        </span>
        <Link className="crumb" to={docsPath(section.id)}>
          {section.title}
        </Link>
        <span className="crumbs__sep" aria-hidden="true">
          ›
        </span>
        <span className="crumbs__here" aria-current="page">
          {title.replace(/\(\)$/, '')}
        </span>
      </nav>
      <div className={styles.kindRow}>
        <span className={styles.kind}>{kind}</span>
      </div>
      <h1 className={styles.h1c}>{title}</h1>
      {lead && <p className={styles.lead} dangerouslySetInnerHTML={html(lead)} />}
      {deprecated && (
        <div className="adm adm--danger">
          <div className="adm__head">
            <span className="adm__title">Deprecated</span>
          </div>
          <div className="adm__body" dangerouslySetInnerHTML={html(deprecated)} />
        </div>
      )}
      <StatusCard id={section.id} title={section.title} status={section.status} />
      <div className="toc-mobile">
        <DocItemTOCMobile />
      </div>
      {importName && (
        <CodeBlock
          language="ts"
          title="Import"
        >{`import { ${importName} } from '@apexnative/navigation';`}</CodeBlock>
      )}
    </header>
  );
}

export function ApiHeader({ data, name }: { data: SectionData; name: string }) {
  const entry = data.entries[name];
  const callable = ['hook', 'function', 'builder'].includes(entry.kind);
  return (
    <Header
      data={data}
      kind={KIND[entry.kind]}
      title={callable ? `${name}()` : name}
      lead={entry.summaryHtml}
      importName={entry.kind === 'interface' || entry.kind === 'type' ? `type ${name}` : name}
      deprecated={entry.deprecated}
    />
  );
}

export function OptionsHeader({ data }: { data: SectionData }) {
  const single = data.options.length === 1 && data.options[0].kids.length > 0;
  const root = data.options[0];
  return (
    <Header
      data={data}
      kind="Options"
      title={single ? root.path : 'Options'}
      lead={
        single
          ? root.html.replace(/^<p>|<\/p>$/g, '')
          : `The options of ${data.section.title.toLowerCase()}.`
      }
    />
  );
}

export function TypesHeader({ data }: { data: SectionData }) {
  return (
    <Header
      data={data}
      kind="Types"
      title="Types"
      lead={`The types of ${data.section.title.toLowerCase()}.`}
    />
  );
}

// --------------------------------------------------------------------------------------- signatures and params

export function ApiSignature({ data, name }: { data: SectionData; name: string }) {
  const entry = data.entries[name];
  return (
    <>
      <CodeBlock language="ts">{entry.signature}</CodeBlock>
      <a className={styles.src} href={entry.source.url}>
        Defined in {entry.source.file} ↗
      </a>
    </>
  );
}

function ParamTable({ params }: { params: Param[] }) {
  return (
    <div className={styles.ptabBox}>
      <table className={styles.ptab}>
        <thead>
          <tr>
            <th scope="col" className={clsx('eyebrow', styles.th)}>
              Parameter
            </th>
            <th scope="col" className={clsx('eyebrow', styles.th)}>
              Type
            </th>
            <th scope="col" className={clsx('eyebrow', styles.th)}>
              Description
            </th>
          </tr>
        </thead>
        <tbody>
          {params.map((param) => (
            <tr key={param.name}>
              <td className={clsx(styles.td, styles.tdName)}>
                <span className={styles.fieldName}>{param.name}</span>
                {!param.optional && <Required />}
              </td>
              <td className={clsx(styles.td, styles.tdType)}>{param.type}</td>
              <td className={styles.td}>
                <span dangerouslySetInnerHTML={html(param.html)} />
                {param.default && (
                  <span className={styles.muted}>
                    {' '}
                    Default <code>{param.default}</code>.
                  </span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function ApiParams({ data, name }: { data: SectionData; name: string }) {
  return <ParamTable params={data.entries[name].params ?? []} />;
}

export function ApiExamples({ data, name }: { data: SectionData; name: string }) {
  return (
    <>
      {(data.entries[name].examples ?? []).map((code, i) => (
        <CodeBlock key={i} language="tsx" title="Example">
          {code}
        </CodeBlock>
      ))}
    </>
  );
}

// ------------------------------------------------------------------------------------------------- members

function signatureParts(member: Member) {
  const first = member.signature.split('\n')[0];
  return first.startsWith(member.name)
    ? [member.name, first.slice(member.name.length)]
    : [member.name, `: ${first}`];
}

export function ApiMembers({ data, name }: { data: SectionData; name: string }) {
  const members = useMemo(() => data.entries[name]?.members ?? [], [data, name]);
  const groups = data.section.memberGroups;
  const chips = useMemo(() => {
    const used = members.map((m) => groups[m.name]).filter(Boolean);
    return used.length ? ['All', ...Array.from(new Set(used))] : [];
  }, [members, groups]);
  const [filter, setFilter] = useState('All');
  const [open, setOpen] = useState<Record<string, boolean>>({
    [members.find((m) => m.method)?.name ?? '']: true,
  });
  const shown = members.filter((m) => filter === 'All' || groups[m.name] === filter);
  const sectionId = (title: string) =>
    Object.entries(data.sections).find(([, t]) => t === title)?.[0];
  // "On this page" links to the cards; the link checker of the build learns their ids here.
  const brokenLinks = useBrokenLinks();
  for (const member of members) brokenLinks.collectAnchor(`member-${member.name}`);
  return (
    <div>
      {chips.length > 0 && (
        <div className={styles.chips} role="group" aria-label="Filter the members">
          {chips.map((chip) => (
            <button
              key={chip}
              type="button"
              className={clsx(styles.chip, filter === chip && styles.chipOn)}
              aria-pressed={filter === chip}
              onClick={() => setFilter(chip)}
            >
              {chip}
            </button>
          ))}
        </div>
      )}
      {shown.map((member) => {
        const isOpen = !!open[member.name];
        const [head, rest] = signatureParts(member);
        const group = groups[member.name];
        const groupId = group ? sectionId(group) : undefined;
        return (
          <div key={member.name} id={`member-${member.name}`} className={styles.mem}>
            <button
              type="button"
              className={styles.memHead}
              aria-expanded={isOpen}
              onClick={() => setOpen({ ...open, [member.name]: !isOpen })}
            >
              <span className={styles.memText}>
                <span className={styles.sig}>
                  <span className={styles.sigN}>{head}</span>
                  {rest}
                </span>
                <span className={styles.memShort}>{member.summary}</span>
              </span>
              {group && <span className={styles.grp}>{group}</span>}
              <Chevron open={isOpen} />
            </button>
            {isOpen && (
              <div className={styles.memBody}>
                {member.signature.includes('\n') && (
                  <CodeBlock language="ts">{member.signature}</CodeBlock>
                )}
                <div className={styles.desc} dangerouslySetInnerHTML={html(member.html)} />
                {member.params.length > 0 && <ParamTable params={member.params} />}
                <div className={styles.facts}>
                  <span className={clsx('eyebrow', styles.factLabel)}>
                    {member.method ? 'Returns' : 'Type'}
                  </span>
                  <span>
                    <code>{member.method ? member.returns : member.type}</code>
                    {member.default && (
                      <span className={styles.factNote}> Default {member.default}.</span>
                    )}
                  </span>
                  <span className={clsx('eyebrow', styles.factLabel)}>Platforms</span>
                  <Platforms platforms={member.platforms} since={member.since} />
                </div>
                {member.deprecated && (
                  <div className={styles.note}>
                    <strong>Deprecated.</strong>{' '}
                    <span dangerouslySetInnerHTML={html(member.deprecated)} />
                  </div>
                )}
                <div className={styles.memLinks}>
                  {group && groupId && (
                    <Link to={docsPath(groupId)} className={styles.memSection}>
                      Section: {group} ›
                    </Link>
                  )}
                  <a href={member.source.url} className={styles.memSource}>
                    Source ↗
                  </a>
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

/**
 * The facts of one member under its heading on a written reference page: the signature, the type or what it
 * returns, the default and the platforms. The text around it explains the member.
 */
export function ApiMember({ data, of, name }: { data: SectionData; of: string; name: string }) {
  const member = data.entries[of]?.members?.find((m) => m.name === name);
  if (!member) throw new Error(`ApiMember: ${of}.${name} is not in the data of ${data.section.id}`);
  return (
    <div className={styles.memberFacts}>
      <CodeBlock language="ts">{member.signature}</CodeBlock>
      <div className={styles.facts}>
        {!member.method && member.type && (
          <>
            <span className={clsx('eyebrow', styles.factLabel)}>Type</span>
            <span>
              <code>{member.type}</code>
              {!member.optional && <span className={styles.factNote}> Required.</span>}
            </span>
          </>
        )}
        {member.method && member.returns && (
          <>
            <span className={clsx('eyebrow', styles.factLabel)}>Returns</span>
            <code>{member.returns}</code>
          </>
        )}
        {member.default && (
          <>
            <span className={clsx('eyebrow', styles.factLabel)}>Default</span>
            <code>{member.default}</code>
          </>
        )}
        <span className={clsx('eyebrow', styles.factLabel)}>Platforms</span>
        <Platforms platforms={member.platforms} since={member.since} />
      </div>
      {member.deprecated && (
        <div className={styles.note}>
          <strong>Deprecated.</strong> <span dangerouslySetInnerHTML={html(member.deprecated)} />
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------------------------------- types

export function ApiType({ data, name }: { data: SectionData; name: string }) {
  const entry = data.entries[name];
  return (
    <div className={styles.type}>
      <div className={styles.kindRow}>
        <span className={styles.kind}>{KIND[entry.kind]}</span>
      </div>
      {entry.html && <div className={styles.desc} dangerouslySetInnerHTML={html(entry.html)} />}
      {entry.deprecated && (
        <div className={styles.note}>
          <strong>Deprecated.</strong> <span dangerouslySetInnerHTML={html(entry.deprecated)} />
        </div>
      )}
      <CodeBlock language="ts">{entry.signature}</CodeBlock>
      {entry.members && entry.members.length > 0 && (
        <div className={styles.ptabBox}>
          <table className={styles.ptab}>
            <thead>
              <tr>
                <th scope="col" className={clsx('eyebrow', styles.th)}>
                  Member
                </th>
                <th scope="col" className={clsx('eyebrow', styles.th)}>
                  Type
                </th>
                <th scope="col" className={clsx('eyebrow', styles.th)}>
                  Description
                </th>
              </tr>
            </thead>
            <tbody>
              {entry.members.map((member) => (
                <tr key={member.name}>
                  <td className={clsx(styles.td, styles.tdName)}>
                    <span className={styles.fieldName}>{member.name}</span>
                    {!member.optional && !member.method && <Required />}
                  </td>
                  <td className={clsx(styles.td, styles.tdType)}>
                    {member.method ? member.signature : member.type}
                  </td>
                  <td className={styles.td}>
                    <div className={styles.cellText} dangerouslySetInnerHTML={html(member.html)} />
                    {member.platforms.length < 3 && (
                      <Platforms platforms={member.platforms} since={member.since} />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <a className={styles.src} href={entry.source.url}>
        Defined in {entry.source.file} ↗
      </a>
    </div>
  );
}

// ------------------------------------------------------------------------------------ hand-written page parts

/** The code of one idea in several places, as tabs in the header of a block (DocsOptions: "Set it"). */
export function CodeTabs({
  tabs,
}: {
  tabs: { label: string; file: string; language?: string; code: string }[];
}) {
  const [index, setIndex] = useState(0);
  const theme = usePrismTheme();
  const tab = tabs[index];
  return (
    <div className="cb">
      <div className="cb__head">
        <div role="tablist" aria-label="Where the options are set" className="cb__tabs">
          {tabs.map((t, i) => (
            <button
              key={t.label}
              type="button"
              role="tab"
              aria-selected={i === index}
              className={i === index ? 'cb__tab cb__tab--on' : 'cb__tab'}
              onClick={() => setIndex(i)}
            >
              {t.label}
            </button>
          ))}
        </div>
        <span className={clsx('cb__title', styles.tabsFile)}>{tab.file}</span>
      </div>
      <div className="cb__body">
        <Highlight code={tab.code.trim()} language={tab.language ?? 'tsx'} theme={theme}>
          {({ tokens, getLineProps, getTokenProps }) => (
            <pre className="prism-code">
              <code>
                {tokens.map((line, i) => (
                  <span key={i} {...getLineProps({ line })}>
                    {line.map((token, j) => (
                      <span key={j} {...getTokenProps({ token })} />
                    ))}
                    {'\n'}
                  </span>
                ))}
              </code>
            </pre>
          )}
        </Highlight>
      </div>
    </div>
  );
}

/** The cards at the end of a reference page (DocsApi: "See also"). */
export function SeeAlso({ items }: { items: { name: string; text: string; to: string }[] }) {
  return (
    <div className={styles.alsoGrid}>
      {items.map((item) => (
        <Link key={item.name} to={item.to} className={styles.also}>
          <span className={styles.alsoName}>{item.name}</span>
          <span className={styles.alsoText}>{item.text}</span>
        </Link>
      ))}
    </div>
  );
}

/** The last row of a reference page: where it comes from. */
export function ApiFooter({ url }: { url: string }) {
  return (
    <div className="doc-foot">
      <span>
        Generated from the types and their comments.{' '}
        <a className="doc-foot__edit" href={url}>
          Edit the source ↗
        </a>
      </span>
    </div>
  );
}

// --------------------------------------------------------------------------------------------- options tree

interface Row {
  node: OptionNode;
  depth: number;
  open: boolean;
}

function pathsWithKids(nodes: OptionNode[], out: Record<string, boolean> = {}) {
  for (const node of nodes) {
    if (node.kids.length) {
      out[node.path] = true;
      pathsWithKids(node.kids, out);
    }
  }
  return out;
}

export function OptionsTree({ data }: { data: SectionData }) {
  const single = data.options.length === 1 && data.options[0].kids.length > 0;
  const top = single ? data.options[0].kids : data.options;
  const [query, setQuery] = useState('');
  const [platform, setPlatform] = useState<'all' | Platform>('all');
  const [deprecated, setDeprecated] = useState(false);
  const [open, setOpen] = useState<Record<string, boolean>>(() => {
    const first = top.find((node) => node.kids.length);
    return first ? { [first.path]: true } : {};
  });

  const needle = query.trim().toLowerCase();
  const walk = (nodes: OptionNode[], depth: number, passed: boolean): Row[] => {
    const out: Row[] = [];
    for (const node of nodes) {
      if (platform !== 'all' && !node.platforms.includes(platform)) continue;
      if (!deprecated && node.deprecated) continue;
      const self =
        passed || !needle || `${node.path} ${node.summary}`.toLowerCase().includes(needle);
      const kids = walk(node.kids, depth + 1, self);
      if (!self && kids.length === 0) continue;
      const isOpen = needle ? true : !!open[node.path];
      out.push({ node, depth, open: isOpen });
      if (node.kids.length && isOpen) out.push(...kids);
    }
    return out;
  };
  const rows = walk(top, 0, false);
  const brokenLinks = useBrokenLinks();
  for (const row of rows) brokenLinks.collectAnchor(`option-${row.node.path}`);

  return (
    <div>
      <div className={styles.otool}>
        <label className="sr-only" htmlFor="option-filter">
          Filter options
        </label>
        <input
          id="option-filter"
          className={styles.fin}
          type="search"
          placeholder="Filter options"
          autoComplete="off"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <div className={styles.seg} role="group" aria-label="Platform">
          {[{ id: 'all' as const, label: 'All' }, ...PLATFORMS].map(({ id, label }) => (
            <button
              key={id}
              type="button"
              className={clsx(styles.sb, platform === id && styles.sbOn)}
              aria-pressed={platform === id}
              onClick={() => setPlatform(id)}
            >
              {label}
            </button>
          ))}
        </div>
        <label className={styles.check}>
          <input type="checkbox" checked={deprecated} onChange={() => setDeprecated(!deprecated)} />
          Deprecated
        </label>
        <button type="button" className={styles.tbtn} onClick={() => setOpen(pathsWithKids(top))}>
          Expand all
        </button>
        <button type="button" className={styles.tbtn} onClick={() => setOpen({})}>
          Collapse all
        </button>
      </div>
      <p className={styles.count} aria-live="polite">
        {rows.length === 1 ? '1 option shown' : `${rows.length} options shown`}
      </p>
      <div className={styles.otree}>
        {rows.map(({ node, depth, open: isOpen }) => (
          <div
            key={node.path}
            id={`option-${node.path}`}
            className={styles.orow}
            data-depth={depth}
          >
            <span className={styles.rails} style={{ width: depth * 22 }} aria-hidden="true" />
            <div className={styles.ocell}>
              <div className={styles.oline}>
                {node.kids.length > 0 ? (
                  <button
                    type="button"
                    className={styles.tog}
                    aria-expanded={isOpen}
                    aria-label={`${isOpen ? 'Collapse' : 'Expand'} ${node.path}`}
                    onClick={() => setOpen({ ...open, [node.path]: !isOpen })}
                  >
                    <Chevron open={isOpen} size={12} />
                  </button>
                ) : (
                  <span className={styles.togSp} aria-hidden="true" />
                )}
                <a
                  className={clsx(styles.oname, node.deprecated && styles.onameDep)}
                  href={`#option-${node.path}`}
                >
                  {depth === 0 && !single ? node.path : node.name}
                </a>
                {node.required && <Required />}
                <span className={styles.otype}>{node.type}</span>
                {node.default && <span className={styles.odef}>= {node.default}</span>}
                {node.deprecated && <span className={styles.deptag}>Deprecated</span>}
              </div>
              {node.html && (
                <div className={styles.odesc} dangerouslySetInnerHTML={html(node.html)} />
              )}
              {node.deprecated && (
                <div className={styles.odesc} dangerouslySetInnerHTML={html(node.deprecated)} />
              )}
              {node.link && (
                <Link className={styles.olink} to={apiPath(node.link.id, 'options')}>
                  Section: {node.link.title} ›
                </Link>
              )}
              {node.typeLinks?.map((link) => (
                <Link
                  key={link.name}
                  className={styles.olink}
                  to={`${apiPath(link.section, 'types')}#${link.name.toLowerCase()}`}
                >
                  Type: {link.name} ›
                </Link>
              ))}
            </div>
            <div className={styles.oside}>
              <Platforms platforms={node.platforms} since={node.since} />
              {node.showcase?.map((s) => (
                <a key={s.id} className={styles.scl} href={s.url}>
                  Showcase {s.id} ›
                </a>
              ))}
            </div>
          </div>
        ))}
        {rows.length === 0 && (
          <p className={styles.empty}>No options match “{query}” on this platform.</p>
        )}
      </div>
    </div>
  );
}
