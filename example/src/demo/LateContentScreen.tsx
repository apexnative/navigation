import React, { useEffect, useState } from 'react';
import { defineScreen } from '@apexnative/navigation';
import { Card, Muted, Screen } from '../ui';

/**
 * A screen that draws nothing (`null`) for a second and then its content, as a screen that waits for its data without
 * a loading view.
 */
function LateContent() {
  const [shown, setShown] = useState(false);
  useEffect(() => {
    const timer = setTimeout(() => setShown(true), 1000);
    return () => clearTimeout(timer);
  }, []);
  if (!shown) return null;
  return (
    <Screen>
      <Card title="Content">
        <Muted>The screen drew nothing for a second, then this.</Muted>
      </Card>
    </Screen>
  );
}

export const LateContentScreen = defineScreen({
  component: LateContent,
  options: (s) => s.title('Late content').hideBottomTabs(),
});
