import React, { createContext, ReactNode, useContext, useMemo, useState } from 'react';

interface SavedItems {
  count: number;
  save: () => void;
}

const SavedItemsContext = createContext<SavedItems | null>(null);

/**
 * A store of the Showcase: how many items the user saved. As `appProvider` one store serves every screen; as
 * `screenProvider` each screen has a store of its own.
 */
export function SavedItemsProvider({ children }: { children: ReactNode }) {
  const [count, setCount] = useState(0);
  const value = useMemo(() => ({ count, save: () => setCount((current) => current + 1) }), [count]);
  return <SavedItemsContext.Provider value={value}>{children}</SavedItemsContext.Provider>;
}

export function useSavedItems(): SavedItems | null {
  return useContext(SavedItemsContext);
}
