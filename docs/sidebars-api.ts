import type { SidebarsConfig } from '@docusaurus/plugin-content-docs';
import fs from 'node:fs';
import path from 'node:path';

interface SidebarSection {
  id: string;
  title: string;
  status: string;
  docs: string[];
}

// Written by scripts/api.mjs next to the reference pages it generates.
const groups: { id: string; title: string; sections: SidebarSection[] }[] = JSON.parse(
  fs.readFileSync(path.join(__dirname, '.api/sidebar.json'), 'utf8')
);

const sidebars: SidebarsConfig = {
  api: groups.map((group) => ({
    type: 'category' as const,
    label: group.title,
    collapsible: true,
    collapsed: false,
    items: group.sections.map((section) => ({
      type: 'category' as const,
      label: section.title,
      className: `sidebar-status sidebar-status--${section.status}`,
      collapsible: true,
      collapsed: true,
      link: { type: 'doc' as const, id: section.docs[0] },
      items: section.docs,
    })),
  })),
};

export default sidebars;
