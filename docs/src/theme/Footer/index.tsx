import Link from '@docusaurus/Link';
import { useLocation } from '@docusaurus/router';
import useBaseUrl from '@docusaurus/useBaseUrl';
import React from 'react';

const COLUMNS: { title: string; links: [string, string][] }[] = [
  {
    title: 'Docs',
    links: [
      ['Quick start', '/docs/start/quick-start'],
      ['Stack', '/docs/containers/stack'],
      ['Tabs', '/docs/containers/tabs'],
      ['Screen options', '/docs/fundamentals/screen-options'],
      ['Guides', '/docs/guides/overview'],
      ['State management', '/docs/guides/state-management'],
      ['From React Navigation', '/docs/migration/from-react-navigation'],
    ],
  },
  {
    title: 'API',
    links: [
      ['createNavigation', '/api/starting-the-app/createNavigation'],
      ['defineScreen', '/api/screens-and-props/defineScreen'],
      ['useNavigation', '/api/moving-between-screens/useNavigation'],
    ],
  },
  {
    title: 'Project',
    links: [
      ['GitHub', 'https://github.com/apexnative/navigation'],
      ['Issues', 'https://github.com/apexnative/navigation/issues'],
      ['Blog', '/blog'],
      ['apexnative.dev', 'https://apexnative.dev'],
    ],
  },
];

function Fleet() {
  return (
    <div className="footer__fleet-row">
      <span className="footer__fleet">
        <img src={useBaseUrl('/img/apexnative.svg')} alt="" width={26} height={26} />
        <span>Part of the apexnative fleet</span>
      </span>
      <span className="footer__license">MIT License · © 2026 ApexNative</span>
    </div>
  );
}

// The home page has the big footer of the DocsHome board; the other pages have the one row of DocsPage.
export default function Footer(): React.JSX.Element {
  const home = useLocation().pathname === useBaseUrl('/');
  const logo = useBaseUrl('/img/navigation.svg');
  if (!home) {
    return (
      <footer className="footer footer--row">
        <div className="footer__inner">
          <Fleet />
        </div>
      </footer>
    );
  }
  return (
    <footer className="footer footer--home">
      <div className="footer__inner footer__top">
        <div className="footer__brand">
          <div className="footer__brand-name">
            <img src={logo} alt="" width={40} height={40} />
            <span>navigation</span>
          </div>
          <p>Native navigation for React Native on iOS, iPadOS and Android.</p>
        </div>
        <nav aria-label="Footer" className="footer__columns">
          {COLUMNS.map((column) => (
            <div key={column.title} className="footer__column">
              <span className="footer__eyebrow">{column.title}</span>
              {column.links.map(([label, to]) => (
                <Link key={label} className="footer__link-an" to={to}>
                  {label}
                </Link>
              ))}
            </div>
          ))}
        </nav>
      </div>
      <div className="footer__inner footer__bottom">
        <Fleet />
      </div>
    </footer>
  );
}
