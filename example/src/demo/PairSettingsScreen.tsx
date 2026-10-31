import React, { useState } from 'react';
import { defineScreen, useNavigation } from '@apexnative/navigation';
import { Button, Card, Code, Label, Muted, Screen, Segmented } from '../ui';
import {
  PAIR_COMPACTS,
  PAIR_EDGES,
  PAIR_LEADS,
  PAIR_STYLES,
  type PairCompact,
  type PairEdge,
  type PairLead,
  type PairStyle,
  pairVariantName,
} from './pairVariants';

/**
 * The settings of a pair of Profile and Details, of 11.7: which screen leads (its bar, alone outside a large window),
 * the side it takes, the style and what a small window shows. Show pushes the pair with them.
 */
function PairSettings() {
  const nav = useNavigation();
  const [lead, setLead] = useState<PairLead>('Details');
  const [style, setStyle] = useState<PairStyle>('split');
  const [edge, setEdge] = useState<PairEdge>('trailing');
  const [compact, setCompact] = useState<PairCompact>('primary');
  const other = lead === 'Profile' ? 'Details' : 'Profile';
  return (
    <Screen>
      <Card title="The pair">
        <Label>primary: alone when folded, the bar</Label>
        <Segmented
          words
          segments={PAIR_LEADS.map((value) => ({
            key: value,
            label: value,
            selected: lead === value,
            onPress: () => setLead(value),
          }))}
        />
        <Label>primaryEdge: the primary’s side</Label>
        <Segmented
          segments={PAIR_EDGES.map((value) => ({
            key: value,
            label: value,
            selected: edge === value,
            onPress: () => setEdge(value),
          }))}
        />
        <Label>style</Label>
        <Segmented
          segments={PAIR_STYLES.map((value) => ({
            key: value,
            label: value,
            selected: style === value,
            onPress: () => setStyle(value),
          }))}
        />
        <Label>compact: a phone, iPhone Duo folded</Label>
        <Segmented
          segments={PAIR_COMPACTS.map((value) => ({
            key: value,
            label: value,
            selected: compact === value,
            onPress: () => setCompact(value),
          }))}
        />
        <Muted>
          New: primaryEdge works for split too. trailing or bottom puts the primary second: on the
          right side by side, at the bottom one above the other. leading or top puts it first. The
          primary is the screen that shows alone when iPhone Duo is folded, so pick it and its side
          here, then open and fold the device: the primary keeps its place, the other screen comes
          and goes beside it.
        </Muted>
      </Card>
      <Card title="Show">
        <Code>{`defineArrangement({
  primary: '${lead}',
  secondary: '${other}',
  style: '${style}',
  primaryEdge: '${edge}',
  compact: '${compact}',
})`}</Code>
        <Button
          title="Show the pair"
          variant="primary"
          onPress={() =>
            nav.push(pairVariantName(lead, style, edge, compact), { props: { id: '4' } })
          }
        />
      </Card>
    </Screen>
  );
}

export const PairSettingsScreen = defineScreen({
  component: PairSettings,
  options: (s) => s.title('Pair settings'),
});
