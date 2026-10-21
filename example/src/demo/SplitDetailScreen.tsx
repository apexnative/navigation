import React from 'react';
import { Platform } from 'react-native';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Button, Card, KeyValue, Muted, Screen } from '../ui';

/**
 * The secondary column of the split view demo: what a column before it picked. Its buttons change the split view: its
 * options on iOS, the primary pane on Android.
 */
function SplitDetail({ item, folder }: { item?: string; folder?: string }) {
  const nav = useNavigation();
  return (
    <Screen>
      <Card title={item ?? 'Nothing selected'}>
        <Muted>
          {item
            ? 'Shown here with showInColumn from the column before.'
            : 'Pick a folder in Library, or an item in the middle column.'}
        </Muted>
        {folder ? <KeyValue label="Folder" value={folder} /> : null}
      </Card>
      {Platform.OS === 'android' ? (
        <Card title="Change the split view">
          <Button title="Hide Library" onPress={() => nav.hideColumn('primary')} />
          <Button title="Show Library" onPress={() => nav.showColumn('primary')} />
        </Card>
      ) : (
        <Card title="Change the split view">
          <Button
            title="Only this column"
            onPress={() => nav.setOptions({ splitView: { displayMode: 'secondaryOnly' } })}
          />
          <Button
            title="The sidebar beside it"
            onPress={() => nav.setOptions({ splitView: { displayMode: 'oneBesideSecondary' } })}
          />
          <Button
            title="Show the inspector"
            onPress={() => nav.setOptions({ splitView: { inspector: { visible: true } } })}
          />
          <Button
            title="Hide the inspector"
            onPress={() => nav.setOptions({ splitView: { inspector: { visible: false } } })}
          />
        </Card>
      )}
    </Screen>
  );
}

export const SplitDetailScreen = defineScreen({
  component: SplitDetail,
  options: (s, props: { item?: string; folder?: string }) =>
    s.title(props.item ?? 'Details').android((a) => a.orientation('sensor')),
});
