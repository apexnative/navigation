import React, { useRef } from 'react';
import { ScrollView } from 'react-native';
import { defineScreen } from '@apexnative/navigation';
import { useClipEvent } from '../bus';
import { useColors } from '../colors';
import { useClipNavigation } from '../nav';
import { Row } from '../Row';
import { List } from '../../ui';
import { useBottomPadding } from '../insets';

const PLACES = [
  'Harbor Café',
  'City Library',
  'Riverside Park',
  'Old Market',
  'Glass Museum',
  'Corner Bakery',
  'Tram Station',
  'Botanic Garden',
  'Book Exchange',
  'Night Market',
  'Lighthouse',
  'Concert Hall',
  'Flower Stall',
  'Ferry Pier',
];

/** The places near the pin, in a sheet over the map: the sheet's detents and a scroll inside it. */
function Places() {
  const c = useColors();
  const nav = useClipNavigation();
  const bottom = useBottomPadding(16);
  const list = useRef<ScrollView>(null);
  // A step of the clip moves the sheet to a detent, as a drag would.
  useClipEvent('detent', (detent) =>
    nav.setOptions({ modal: { sheet: { selectedDetent: detent } } })
  );
  useClipEvent('scroll', (y) => list.current?.scrollTo({ y: Number(y), animated: true }));
  return (
    <ScrollView
      ref={list}
      contentContainerStyle={{ padding: 16, paddingTop: 24, ...bottom }}
      style={{ backgroundColor: c.background }}
    >
      <List>
        {PLACES.map((title, index) => (
          <Row
            key={title}
            index={index + 2}
            title={title}
            subtitle={`${(index % 6) + 2} min walk`}
          />
        ))}
      </List>
    </ScrollView>
  );
}

export const PlacesScreen = defineScreen({ component: Places });
