import Link from '@docusaurus/Link';
import {
  useActivePlugin,
  useDocsPreferredVersion,
  useDocsVersion,
  useDocVersionSuggestions,
} from '@docusaurus/plugin-content-docs/client';
import type { Props } from '@theme/DocVersionBanner';
import clsx from 'clsx';
import React, { type ReactNode } from 'react';

// The banner over every page of a version that isn't a stable release, in the docs and in the API reference, styled
// as an admonition. A pre-release (1.0.0-alpha.0, 2.0.0-rc.1) says what its stage promises and how to install it; a
// version still in the works, or one that no longer gets updates, says so. Once a released version exists, the banner
// links to the same page in it. Which version gets a banner: `versions` in docusaurus.config.ts.

const STAGES: Record<string, { name: string; promise: string }> = {
  alpha: {
    name: 'Alpha',
    promise:
      'Not a stable release: the API can still change from one alpha to the next, and sections marked Draft are not final.',
  },
  beta: {
    name: 'Beta',
    promise:
      'Not a stable release: everything planned for this version is in, and the API changes only where testing shows it must.',
  },
  rc: {
    name: 'Release candidate',
    promise: 'The API is final; only fixes land until the release.',
  },
};

const ICON = (
  <svg aria-hidden="true" viewBox="0 0 20 20">
    <circle
      cx="10"
      cy="10"
      r="8"
      style={{ fill: 'none', stroke: 'currentColor', strokeWidth: 2 }}
    />
    <path
      d="M10 9 V14 M10 6 V6.2"
      style={{ fill: 'none', stroke: 'currentColor', strokeWidth: 2.2, strokeLinecap: 'round' }}
    />
  </svg>
);

export default function DocVersionBanner({ className }: Props): ReactNode {
  const version = useDocsVersion();
  if (!version.banner) return null;
  return (
    <Banner
      className={className}
      name={version.version}
      label={version.label}
      unmaintained={version.banner === 'unmaintained'}
    />
  );
}

function Banner({
  className,
  name,
  label,
  unmaintained,
}: {
  className?: string;
  name: string;
  label: string;
  unmaintained: boolean;
}) {
  const { pluginId } = useActivePlugin({ failfast: true })!;
  const { latestDocSuggestion, latestVersionSuggestion } = useDocVersionSuggestions(pluginId);
  const { savePreferredVersionName } = useDocsPreferredVersion(pluginId);
  // While the docs have one version, the latest is this one, and there is nothing to link to.
  const released = latestVersionSuggestion.name === name ? null : latestVersionSuggestion;
  const releasedPath =
    released &&
    (latestDocSuggestion ?? released.docs.find((doc) => doc.id === released.mainDocId))?.path;
  const stageId = unmaintained ? undefined : /-(alpha|beta|rc)\b/.exec(label)?.[1];
  const stage = stageId ? STAGES[stageId] : undefined;
  const title = stage ? stage.name : unmaintained ? 'Older version' : 'Next version';

  return (
    <aside
      className={clsx(
        'adm',
        unmaintained ? 'adm--warning' : 'adm--info',
        'version-banner',
        className
      )}
      aria-label={`${title} ${label}`}
    >
      <div className="adm__head">
        {ICON}
        <span className="adm__title">
          {title} · <span className="version-banner__label">{label}</span>
        </span>
      </div>
      <div className="adm__body">
        {stage ? (
          <p>
            {stage.promise} Install it with{' '}
            <code>npm install @apexnative/navigation@{stageId}</code>.
          </p>
        ) : unmaintained ? (
          <p>This version no longer gets updates.</p>
        ) : (
          <p>This version isn't released yet: what it describes can still change.</p>
        )}
        {released && releasedPath ? (
          <p>
            The released version is {released.label}:{' '}
            <Link to={releasedPath} onClick={() => savePreferredVersionName(released.name)}>
              {latestDocSuggestion ? 'this page in it' : 'its documentation'}
            </Link>
            .
          </p>
        ) : null}
      </div>
    </aside>
  );
}
