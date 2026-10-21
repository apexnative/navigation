# Showcase

The app that checks the library, section by section, on the new API. A section is a control panel: its options in
groups, each group a file. Pick the values, press Push or Modal, and the Details screen shows with them; on Details
the same groups change the options while it shows. The panel shows the code of what it runs.

## Run it

```sh
yarn example start      # Metro, port 8081
yarn example ios        # or open example/ios/Showcase.xcworkspace in Xcode
```

## Where things are

```
src/
  app/                 the app: what createNavigation gets
    App.ts             createNavigation({ ... }) and the Register types
    roots.ts           the roots switchTo takes by name
    screens.ts         every screen by name
    options.ts         the default options: the look of the Showcase
    listeners.ts       the navigation's events, into the Journal
  sections/            what the Showcase checks
    index.ts           the list of sections; search
    scenario.ts        the types of a section and a scenario
    1-top-bar/         section 1: one file per scenario, index.ts lists them in order
  showcase/            the screens of the Showcase itself: sections, a section's panel, search, journal, settings
    controls/          the panel: a group of options, a row of one, the options and code they make, opening Details
  demo/                the screens the scenarios show: Details; components in the bar: TitleView, BarBackground
  ui/                  components, one per file, and the theme
babel/scenario-source.js   puts the source of each button next to it, for the app to show
```

## Add a group of options

1. Make a file in the section's folder, named by its number: `sections/1-top-bar/1.11-something.ts`.

   ```ts
   import { color, oneOf, presets, text } from '../controls';
   import { defineScenario } from '../scenario';

   export default defineScenario({
     id: '1.11',
     title: 'What it shows',
     check: 'What to look at; `backticks` mark code.',
     controls: {
       'topBar.subtitle.text': text(),
       'topBar.subtitle.fontSize': oneOf([11, 13, 15]),
       'topBar.subtitle.color': color(),
       'topBar.title.component': presets({ TitleView: { name: 'TitleView' } }),
       'topBar.subtitle.alignment': oneOf(['center'], { platform: 'android' }),
     },
   });
   ```

   Each control sets the option of its path; unset (—), the option is not sent. `runs` are buttons for what the
   controls don't cover; the panel shows their code.

2. Add it to the section's `index.ts`, in its place, and the section to `sections/index.ts`.
