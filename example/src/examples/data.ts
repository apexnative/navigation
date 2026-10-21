/**
 * Made-up people, clubs, posts and events for the examples. The pictures come from the network: drawn avatars
 * (DiceBear) and photos (Picsum), by a seed, so each one is always the same.
 */

export function avatarUrl(seed: string) {
  return `https://api.dicebear.com/9.x/notionists/png?seed=${encodeURIComponent(seed)}&size=128&backgroundColor=c0aede,d1d4f9,ffd5dc,ffdfbf`;
}

export function photoUrl(seed: string, width = 800, height = 500) {
  return `https://picsum.photos/seed/${encodeURIComponent(seed)}/${width}/${height}`;
}

export interface Person {
  id: string;
  name: string;
}

export interface Club {
  id: string;
  name: string;
  members: number;
}

export interface Post {
  id: string;
  clubId: string;
  authorId: string;
  ago: string;
  title: string;
  body: string[];
  likes: number;
  comments: number;
}

export interface Event {
  id: string;
  title: string;
  day: string;
  month: string;
  weekday: string;
  time: string;
  ends: string;
  place: string;
  about: string;
  going: number;
  clubId: string;
}

export const me: Person = { id: 'me', name: 'Ada Park' };

export const people: Person[] = [
  me,
  { id: 'leo', name: 'Leo Martins' },
  { id: 'mia', name: 'Mia Novak' },
  { id: 'sam', name: 'Sam Rivera' },
];

export const clubs: Club[] = [
  { id: 'design', name: 'Product Design Circle', members: 1284 },
  { id: 'running', name: 'Early Risers Running', members: 342 },
  { id: 'books', name: 'Slow Readers Club', members: 517 },
  { id: 'cooks', name: 'Home Cooks', members: 2093 },
];

export const posts: Post[] = [
  {
    id: 'critiques',
    clubId: 'design',
    authorId: 'leo',
    ago: '2h',
    title: 'Thursday critiques: a year of reviewing designs together',
    body: [
      'A year ago we started a thirty-minute critique every Thursday. No slides, one screen, three people who had not seen it before.',
      'The first months were awkward: people defended their work instead of listening. What changed it was a simple rule — the author speaks last.',
      'Now the critique is the meeting people ask to keep when the calendar gets full. Here is what made the difference, in order.',
    ],
    likes: 48,
    comments: 12,
  },
  {
    id: 'marathon',
    clubId: 'running',
    authorId: 'mia',
    ago: '5h',
    title: 'From couch to my first half marathon in 16 weeks',
    body: [
      'Sixteen weeks, four runs a week, and one rule: never two hard days in a row.',
      'The long run grew by a kilometre a week; every fourth week it shrank to let the legs catch up.',
    ],
    likes: 96,
    comments: 23,
  },
  {
    id: 'bread',
    clubId: 'cooks',
    authorId: 'sam',
    ago: '1d',
    title: 'The no-knead bread that finally worked for me',
    body: [
      'Flour, water, salt and a pinch of yeast, left alone overnight. The trick was the hot pot with a lid.',
    ],
    likes: 211,
    comments: 40,
  },
];

export const events: Event[] = [
  {
    id: 'e1',
    title: 'Portfolio review night',
    day: '14',
    month: 'OCT',
    time: '18:30',
    weekday: 'Tue',
    ends: '20:00',
    place: 'Studio 4, 12 Harbor Street',
    about:
      'Bring two or three screens you are working on. Each gets ten minutes: five to show, five to listen. Coffee and snacks are on us.',
    going: 38,
    clubId: 'design',
  },
  {
    id: 'e2',
    title: 'Sunrise 10K along the river',
    day: '18',
    month: 'OCT',
    time: '07:00',
    weekday: 'Sat',
    ends: '08:30',
    place: 'East gate of the river park',
    about:
      'An easy 10K along the water at sunrise, at a pace everyone can talk at. We finish at the bakery by the bridge.',
    going: 21,
    clubId: 'running',
  },
  {
    id: 'e3',
    title: 'Autumn soups swap',
    day: '23',
    month: 'OCT',
    time: '19:00',
    weekday: 'Thu',
    ends: '21:00',
    place: 'Shared kitchen, 3 Mill Lane',
    about:
      'Cook a pot of your favourite soup, bring jars, and go home with five new ones. Recipes are shared after.',
    going: 54,
    clubId: 'cooks',
  },
];

export function findPerson(id: string) {
  return people.find((person) => person.id === id) ?? me;
}

export function findClub(id: string) {
  return clubs.find((club) => club.id === id) ?? clubs[0]!;
}

export function findEvent(id: string) {
  return events.find((event) => event.id === id) ?? events[0]!;
}

export function findPost(id: string) {
  return posts.find((post) => post.id === id) ?? posts[0]!;
}
