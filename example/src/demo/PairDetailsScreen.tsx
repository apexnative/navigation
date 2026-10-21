import React from 'react';
import { StyleSheet } from 'react-native';
import { defineScreen, useArrangement } from '@apexnative/navigation';
import { brand, Card, KeyValue, Muted, Screen } from '../ui';
import { describePair, PairBadge } from './PairProfileScreen';

/** The secondary of the pair demo of 11.7, tinted teal: beside or under Profile, out of the window when there is no room. */
function PairDetails({ id }: { id: string }) {
  const pair = useArrangement();
  return (
    <Screen style={[styles.tint, { backgroundColor: SECONDARY }]}>
      <PairBadge text="SECONDARY · Details" color={brand.teal} />
      <Card title={`Details of ${id}`}>
        <Muted>
          The second screen of the pair, a screen of its own with its own props. Its bar options
          have no effect: the bar is Profile’s. In an overlay it lies under Profile, and beside it
          when the device is partly folded.
        </Muted>
        <KeyValue label="useArrangement" value={describePair(pair)} />
      </Card>
    </Screen>
  );
}

const SECONDARY = 'rgba(42, 157, 143, 0.18)';

export const PairDetailsScreen = defineScreen({ component: PairDetails });

const styles = StyleSheet.create({
  tint: { flexGrow: 1 },
});
