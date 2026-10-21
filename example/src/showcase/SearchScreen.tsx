import React, { useMemo, useState } from 'react';
import { defineScreen, useSearchBar } from '@apexnative/navigation';
import { barFonts } from '../app/barFonts';
import { searchScenarios } from '../sections';
import { Muted, Screen } from '../ui';
import { ScenarioList } from './ScenarioList';

/** The search tab: scenarios by number, title, text, code or API name. */
function Search() {
  const [query, setQuery] = useState('');
  useSearchBar({ onChange: (event) => setQuery(event.text) });
  const results = useMemo(() => searchScenarios(query), [query]);
  return (
    <Screen underBar>
      {query.trim() === '' ? (
        <Muted>Type a number, a title or an API name, such as switchTo or willMount.</Muted>
      ) : results.length === 0 ? (
        <Muted>Nothing found for “{query}”.</Muted>
      ) : (
        <ScenarioList scenarios={results} />
      )}
    </Screen>
  );
}

export const SearchScreen = defineScreen({
  component: Search,
  options: (s) =>
    s.title('Search').largeTitle().topBar(barFonts).searchBar({
      visible: true,
      hideOnScroll: false,
      obscuresBackgroundDuringPresentation: false,
      placeholder: 'Number, title or API',
    }),
});
