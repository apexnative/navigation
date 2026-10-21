import React from 'react';
import { defineScreen } from '@apexnative/navigation';
import { Card, KeyValue, Muted, Screen } from '../ui';

/** The inspector of the split view demo (iPadOS 26): a column at the trailing edge. */
function SplitInspector() {
  return (
    <Screen>
      <Card title="Inspector">
        <Muted>A column at the trailing edge, for facts about what Details shows.</Muted>
        <KeyValue label="Kind" value="Document" />
        <KeyValue label="Size" value="24 KB" />
      </Card>
    </Screen>
  );
}

export const SplitInspectorScreen = defineScreen({
  component: SplitInspector,
  options: (s) => s.title('Info'),
});
