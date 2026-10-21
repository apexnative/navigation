import React, { useEffect } from 'react';
import { defineScreen, Screen as ScreenEntry, useNavigation } from '@apexnative/navigation';
import { Button, Card, KeyValue, Muted, Screen } from '../ui';

/** The ids of the steps shown now, by step: popTo goes back to a screen by its id. */
const stepIds = new Map<number, string>();

/**
 * A step of the stack section: the commands of its stack, from the screen. In a modal it also replaces the stack;
 * elsewhere that would take away the Showcase's own screens.
 */
function StackStep({ step, modal }: { step: number; modal?: boolean }) {
  const nav = useNavigation();
  const id = nav.screen.id;
  stepIds.set(step, id);
  useEffect(
    () => () => {
      if (stepIds.get(step) === id) stepIds.delete(step);
    },
    [step, id]
  );
  const first = stepIds.get(1);
  return (
    <Screen>
      <Card title={`Step ${step}`}>
        <KeyValue label="Screen id" value={id} />
        <KeyValue label="canPop · canDismiss" value={`${nav.canPop()} · ${nav.canDismiss()}`} />
        <Button
          title={`Push step ${step + 1}`}
          variant="primary"
          onPress={() => nav.push('StackStep', { props: { step: step + 1, modal } })}
        />
        <Button title="Back: pop" onPress={() => nav.pop()} />
        {nav.canGoBack() ? (
          <Button title="What back would do: goBack" onPress={() => nav.goBack()} />
        ) : null}
        {step > 2 && first ? (
          <Button title="Back to step 1: popTo" onPress={() => nav.popTo(first)} />
        ) : null}
        <Button title="To the first screen: popToRoot" onPress={() => nav.popToRoot()} />
      </Card>
      {modal ? (
        <Card title="Alone in the stack">
          <Muted>
            replace shows step 1 in place of every screen of the stack: there is no back.
          </Muted>
          <Button
            title="Step 1 alone: replace"
            onPress={() => nav.replace('StackStep', { props: { step: 1, modal } })}
          />
        </Card>
      ) : null}
      {modal ? (
        <Card title="Replace the stack">
          <Muted>
            setStack puts these screens in place of every screen of the stack: the last one shows,
            back goes to the one before.
          </Muted>
          <Button
            title="Steps 1, 2 and 3"
            onPress={() =>
              nav.setStack(
                ScreenEntry('StackStep', { step: 1, modal }),
                ScreenEntry('StackStep', { step: 2, modal }),
                ScreenEntry('StackStep', { step: 3, modal })
              )
            }
          />
        </Card>
      ) : null}
    </Screen>
  );
}

export const StackStepScreen = defineScreen({
  component: StackStep,
  options: (s, props: { step: number; modal?: boolean }) =>
    s.title(`Step ${props.step}`).hideBottomTabs(),
});
