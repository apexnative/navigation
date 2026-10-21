import React, { useState } from 'react';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { List, ListRow, Muted, Screen } from '../ui';

/** The middle column of the split view demo: the items of a folder; a pick shows in Details with `showInColumn`. */
function SplitItems({ folder }: { folder?: string }) {
  const nav = useNavigation();
  const [picked, setPicked] = useState<string>();
  if (!folder) return <Muted>Pick a folder in Library.</Muted>;
  const items = Array.from({ length: 12 }, (_, index) => `${folder} ${index + 1}`);
  const pick = (item: string) => {
    setPicked(item);
    nav.showInColumn('SplitDetail', { props: { item, folder } });
  };
  return (
    <Screen>
      <List>
        {items.map((item, index) => (
          <ListRow
            key={item}
            id={index + 1}
            title={item}
            subtitle={item === picked ? 'Selected' : undefined}
            onPress={() => pick(item)}
          />
        ))}
      </List>
    </Screen>
  );
}

export const SplitItemsScreen = defineScreen({
  component: SplitItems,
  options: (s, props: { folder?: string }) => s.title(props.folder ?? 'Items'),
});
