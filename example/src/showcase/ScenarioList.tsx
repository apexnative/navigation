import React from 'react';
import { useNavigation } from '@apexnative/navigation';
import type { Scenario } from '../sections/scenario';
import { List } from '../ui';
import { ScenarioRow } from './ScenarioRow';

/** Groups of options to open on their section's panel, by number and title; those of another platform don't open. */
export function ScenarioList({ scenarios }: { scenarios: Scenario[] }) {
  const nav = useNavigation();
  return (
    <List>
      {scenarios.map((scenario) => (
        <ScenarioRow
          key={scenario.id}
          scenario={scenario}
          chevron="›"
          testID={`scenario-${scenario.id}`}
          onPress={() =>
            nav.push('Section', {
              props: { id: Number(scenario.id.split('.')[0]), focus: scenario.id },
            })
          }
        />
      ))}
    </List>
  );
}
