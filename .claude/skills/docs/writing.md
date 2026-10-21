# Writing a page

How a page is written so that a developer learns from it, finds what they need, and trusts it. The model is the
documentation of React Native (reactnative.dev) and React Navigation (reactnavigation.org): a reader of this library
knows them, and expects the same depth and order. Read this before writing or changing a page, and before an audit
(`audit.md`).

A page is not filled from a template. The headings below are the usual order, not a form: a page has the parts its
subject needs. What every page must do is in "Think first" and "Before the page is done".

## The reader

A React Native developer. They know React and React Native, often React Navigation or Expo Router; they may not know
UIKit or the Android view system. They come to:

- **learn** — the first time, reading from the top: the top of the page teaches them;
- **do a task** — "how do I add Redux", "how do I hide the tab bar on one screen": a guide answers it end to end;
- **look something up** — from search or a link, landing on a heading: the reference and the anchors serve them.

**You** is the reader, the developer. **The user** is the person who uses the app. Never mix them.

## Think first

Before the first line, answer these for the subject of the page. The answers become sections, examples and warnings.
A page that skipped this is the "template" kind of page this project doesn't want.

1. **The situation.** What is the reader building when they need this? Name it in app terms: "a chat opens from a
   notification", not "`push` with props".
2. **What they already know.** The React Navigation / React Native way of doing it. Where this library differs, say so
   and show both (`<Pair>`). Where it is the same, say that in one sentence: it saves them reading.
3. **The mental model.** One sentence of what owns the state and what a call means: "the native side owns the stack;
   `push` asks it to add a screen and resolves when the screen is shown".
4. **What breaks.** What does a reader try first, and what happens? A command called before the app started, a hook
   outside a screen, a provider that doesn't reach a screen, a modal shown twice. Each becomes a section named after the
   situation, with the naive code, why it fails, and the fix.
5. **Each screen is a React root.** Without `appProvider`, every screen is a React root of its own: a React context, a
   provider, a `useState` at the top of the app don't reach the other screens. Think through what this means for the
   subject (stores, query clients, themes of UI kits, gesture handler, portals, error boundaries) and say it where it
   matters, with the way that works.
6. **The platforms.** What iOS, iPadOS and Android each do, what one of them can't do and what happens there instead.
   Check it in the code (`ios/`, `android/`), not by guessing.
7. **Time.** When does it run: at launch, on a relaunch (the user swipes the app away, Android recreates the activity),
   after a JS reload, while a transition runs, when the screen is behind another. What does the user see meanwhile?
8. **The what-ifs.** The questions a reader will ask next ("what if the screen is already open?", "what if I need the
   result back?", "what if I don't use the context?"). Answer them in the same section, or link to where they are.
9. **Other libraries.** Which popular libraries touch the subject (state managers, React Query, Reanimated, Gesture
   Handler, bottom sheets, analytics, i18n, testing), and what they need here. Verify it in their own source or docs.
10. **What to leave out.** What belongs on another page: link to it instead of repeating.

## The kinds of pages

Each group has a kind; a page keeps to it, and links to the other kinds.

| Group                                     | Kind            | The reader asks                                       |
| ----------------------------------------- | --------------- | ----------------------------------------------------- |
| `start/introduction`                      | Explanation     | What is this, and is it for me?                       |
| `start/installation`                      | How-to          | How do I add it to my app?                            |
| `start/quick-start`                       | Tutorial        | Show me it working.                                   |
| `fundamentals`, `containers`, `screen-ui` | Concept + usage | How does this part work, and how do I use it?         |
| `architecture`                            | Explanation     | Why does it work this way? Which way do I pick?       |
| `flows`, `guides`                         | Guide           | How do I build this, or wire this library, in my app? |
| `migration`                               | How-to          | I use another library; how do I move?                 |
| `help`                                    | Lookup          | What does this word mean? Why does this break?        |
| `docs/api/` (the reference)               | Reference       | What exactly does this take, return and do?           |

### Tutorial (Quick start)

One path, no alternatives. Motivation in the first paragraph; each `##` adds one idea to the same running example,
the changed lines marked with `// highlight-next-line` or `{3-5}` in the code fence. Ends with a **Summary** — every
term linked to its reference — and **What's next**.

### Concept + usage page (a section of fundamentals, containers, screen UI)

1. The situation in one to three sentences, then the API that handles it, then the smallest example that works, and
   what the user sees.
2. The mental model in one sentence, before the second example.
3. From the common to the rare: the usual use → the variations → the platforms → over time (lifecycle, relaunch).
   One idea per `##`, each adding to what came before.
4. The pitfalls, each a `##`/`###` named after the situation ("Pushing a screen that is already open").
5. A short list of what to read next, then `## API {#api}` with `<SectionApi id="…" />`.

It shows the use; every key, method and default is in the reference, linked, not repeated.

### Guide (guides, flows)

A guide answers one real task, start to finish, in the reader's app.

1. **The goal** in one or two sentences, and what the result looks like (a `<DeviceVideo>` when motion is the point).
2. **What you need**: the versions, the pages to know first (linked), what the reader's app already has.
3. **How it will work**: the plan in a short list or a diagram, before any code, so the reader knows where each step
   leads.
4. **Steps**, `##` each, numbered when the order matters. Each step: why, the code (a complete file the first time,
   then the changed part), what the reader sees now. A step the reader can check is a step they can trust.
5. **The nuances**, each a `##` named after the situation: what breaks, the platform differences, the what-ifs, what
   the library does differently from React Navigation.
6. **The whole code** at the end when the guide built it in pieces, and **What's next**.

A page about several libraries of one kind (state managers, data fetching) starts with what they share here (the
React root of each screen, `appProvider`), then one `##` per library with the same steps in the same order: install,
create, connect to the screens, read and write from a screen, read and write outside React (from `willMount`, a
link route, a listener), reset on sign-out, test. The reader reads only their library's part, so each part stands on
its own, and the shared part is linked from each.

### Reference page (`docs/api/<section>/<Export>.mdx`)

Written by hand; `docs/scripts/api.mjs` adds the header, the data and the footer, and fails when a member the export
takes or returns has no heading. Shape, as reactnative.dev and React Navigation do it:

1. What it is for, in one or two sentences, and where it is used (`index.js`, a screen, outside React).
2. **Usage**: a complete, runnable example and what it does. A second example when there are two common ways.
3. `## Signature {#signature}` with `<ApiSignature data={data} name="…" />`.
4. One `##` per group of members, in the order a reader needs them ("The screens", "The first root", "Providers",
   "Launch", "Look"), and under it a `###` per member:

   ````mdx
   ### `lazy()` {#lazy}

   <ApiMember data={data} of="TabsElement" name="lazy" />

   Creates each tab the first time it shows, instead of all of them with the tabs. The app starts faster when the tabs
   are heavy, and a tab the user never opens costs nothing.

   ```ts
   Tabs(Tab('Home'), Tab('Inbox')).lazy();
   ```

   The first switch to a tab waits for its screen to render; ….
   ````

   Under each member: what it does and why one uses it; the values, as a list when there are several; the default and
   the platform in a sentence when they matter; a short example when the value isn't obvious from its type; how it
   works with the other members; what breaks. The anchor is the member's exact name, `{#lazy}`.

5. The behavior over time and the caveats of the whole export, then **See also**: the concept page and the guides.

The JSDoc in `src/` stays short — one or two sentences, the facts — because it shows in the editor. The reference
page explains.

## Making it easy to learn

- **The situation before the API, an example before the rule.** A reader understands a rule faster from one concrete
  case than from its general statement.
- **One new thing at a time.** Each example adds one thing to what the reader has seen. A first example that sets
  five keys teaches none of them.
- **Say why.** A rule comes with its reason: "the splash screen stays until `willMount` resolves, so the first screen
  never shows half-ready". A reader who knows why applies it to cases the page doesn't list.
- **Show the result.** After the code, what the user sees and does: "Inbox shows; back goes to Home". For motion, a
  clip (`<DeviceVideo>`) or a demo from `docs/src/components/Screens`.
- **Show the wrong way next to the right one,** when readers will try it: the naive code with a `// Don't` comment,
  why it fails, then the fix.
- **Nothing used before it is explained or linked.** Link to the exact anchor.
- **The common case first.** Rare keys go to the end or to the reference.

## Words

- One word for one thing, everywhere: screen, root, stack, layout, command, options, context, modal, overlay. The
  glossary (`help/glossary.mdx`) has them.
- Plain, short sentences; present tense; active voice. "You" in tutorials and guides; elsewhere the API, the app or the
  user acts.
- Headings say what is under them, in the reader's words: a task, a situation, a thing. Never "Notes", "Details",
  "Advanced". Sentence case. Every `##`/`###` has an explicit anchor `{#id}`.
- No "simply", "just", "easily", "obviously".
- Admonitions with fixed meanings, few per page: `info` where something lives elsewhere, `tip` a recommendation,
  `note` an edge case or a platform detail, `warning` what breaks the app or loses data. The main rule of a section is
  never only in an admonition.
- Site components, no new styles: `Mark`, `Os`, `Pair`, `Steps`, `DeviceVideo`, `Tabs`/`TabItem` of Docusaurus for
  variants (npm/Yarn, iOS/Android, with/without the context).

## Code

- **The real API.** Every name and signature checked against `src/`. An example that doesn't compile is the worst
  error a page can have. Every name an example uses is imported in it or in an earlier example on the page.
- **Complete files** for the first example of a page and of a guide step: imports and `title="src/…"`. Later ones may
  be the changed part, with `// …` for the rest. Complete examples 20–50 lines; one longer example per page at most,
  last.
- At most 72 characters per line. Neutral names: Home, Profile, Settings, Details, Login, Inbox, Chat.
- What the reader writes themselves is marked: `// your own API call`. Third-party libraries are used with their real,
  current API, checked in their docs.

## Platforms

Where behavior differs, `<Os name="…" />` next to the behavior, or a table when several differ. Where a platform can't
do something, the page says what happens there instead.

## Before the page is done

- The first screen: a new reader knows what this is for and sees working code.
- Every point of "Think first" is answered on the page or knowingly left to another page, with a link.
- Every example checked against `src/` and the third-party library's API; every name imported.
- Every member of a reference page has its `###` with `<ApiMember>`, an explanation, and an example where the value
  isn't obvious.
- Headings say what is under them; anchors set; links resolve: `scripts/docs.sh build`.
