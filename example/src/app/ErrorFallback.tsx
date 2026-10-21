import React from 'react';
import type { ErrorFallbackProps } from '@apexnative/navigation';
import { Button, Card, KeyValue, Muted, Screen } from '../ui';

/**
 * What a screen that failed to render shows (`errorFallback`): the error and a way to try again. A start that failed
 * shows it too, as the first screen.
 */
export function ErrorFallback({ error, screen, retry }: ErrorFallbackProps) {
  return (
    <Screen>
      <Card title="Something went wrong">
        <Muted>{error instanceof Error ? error.message : String(error)}</Muted>
        <KeyValue label="Screen" value={screen.name} />
        <Button title="Try again" variant="primary" onPress={retry} />
      </Card>
    </Screen>
  );
}
