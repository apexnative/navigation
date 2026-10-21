import React, { useState } from 'react';
import { defineScreen, navigation, useNavigation } from '@apexnative/navigation';
import { Button, Card, List, ListRow, Muted, Screen } from '../ui';

const FOLDERS = ['Recent', 'Favorites', 'Shared', 'Archive'];

/**
 * The sidebar of the split view demo: its folders. A pick shows in the next column with `showInColumn`: the items of
 * the folder in the middle one with three columns, Details with two. Collapsed, the command puts the column on top, or
 * pushes it in the compact layout.
 */
function SplitSidebar({ next }: { next: 'supplementary' | 'secondary' }) {
  const nav = useNavigation();
  const [picked, setPicked] = useState<string>();
  const pick = (folder: string) => {
    setPicked(folder);
    if (next === 'supplementary') {
      nav.showInColumn('SplitItems', { props: { folder }, column: 'supplementary' });
    } else {
      nav.showInColumn('SplitDetail', { props: { item: folder } });
    }
  };
  return (
    <Screen>
      <List>
        {FOLDERS.map((folder, index) => (
          <ListRow
            key={folder}
            id={index + 1}
            title={folder}
            subtitle={folder === picked ? 'Selected' : undefined}
            onPress={() => pick(folder)}
          />
        ))}
      </List>
      <Card title="A split view">
        <Muted>The columns have the options of the Split view panel.</Muted>
        <Button
          title="Back to the Showcase"
          variant="primary"
          onPress={() => navigation.switchTo('showcase')}
        />
      </Card>
    </Screen>
  );
}

export const SplitSidebarScreen = defineScreen({
  component: SplitSidebar,
  // The Showcase is portrait; the panes go side by side in landscape on Android.
  options: (s) => s.title('Library').android((a) => a.orientation('sensor')),
});
