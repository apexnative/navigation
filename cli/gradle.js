// @ts-check
'use strict';

const fs = require('node:fs');
const path = require('node:path');

/** @typedef {import('./changes').FileChange} FileChange */

// The latest stable Compose BOM when the CLI was written; an app can move to a newer one.
const COMPOSE_BOM = '2026.09.00';

const HOW_TO = `Set up Jetpack Compose in the app:
  android/build.gradle, in buildscript dependencies:
    classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")
  android/app/build.gradle:
    apply plugin: "org.jetbrains.kotlin.plugin.compose"
    android { buildFeatures { compose true } }
    dependencies {
        implementation(platform("androidx.compose:compose-bom:${COMPOSE_BOM}"))
        implementation("androidx.compose.ui:ui")
        implementation("androidx.compose.material3:material3")
    }`;

/**
 * The changes of the Gradle files that set up Jetpack Compose in the app, as React Native's template has them: the
 * Compose compiler plugin of the app's Kotlin version, `buildFeatures.compose`, and the BOM with UI and Material 3.
 * `done` when the app has Compose; by hand when its files aren't as the template's.
 * @param {import('./project').Project} project
 * @returns {FileChange[]}
 */
function composeChanges(project) {
  const { rootBuildGradle, appBuildGradle } = project;
  if (
    !rootBuildGradle ||
    !appBuildGradle ||
    !rootBuildGradle.endsWith('.gradle') ||
    !appBuildGradle.endsWith('.gradle')
  ) {
    return [
      manual(appBuildGradle ?? rootBuildGradle, "the Gradle files aren't those of the template"),
    ];
  }
  const rootBefore = fs.readFileSync(rootBuildGradle, 'utf8');
  const appBefore = fs.readFileSync(appBuildGradle, 'utf8');
  if (
    /androidx\.compose/.test(appBefore) &&
    /compose-compiler-gradle-plugin|plugin\.compose/.test(rootBefore + appBefore)
  ) {
    return [
      {
        name: 'build.gradle',
        platform: 'android',
        file: appBuildGradle,
        status: 'done',
        note: 'Compose set up',
      },
    ];
  }

  const root = insertAfterLine(
    rootBefore,
    /^\s*classpath\(["']org\.jetbrains\.kotlin:kotlin-gradle-plugin["']\)\s*$/m,
    (indent) =>
      `${indent}classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")`
  );
  let app = insertAfterLine(
    appBefore,
    /^apply plugin: ["']org\.jetbrains\.kotlin\.android["']\s*$/m,
    () => 'apply plugin: "org.jetbrains.kotlin.plugin.compose"'
  );
  app =
    app &&
    insertAfterLine(
      app,
      /^android \{\s*$/m,
      () => '    buildFeatures {\n        compose true\n    }'
    );
  app =
    app &&
    insertAfterLine(app, /^dependencies \{\s*$/m, () =>
      [
        `    implementation(platform("androidx.compose:compose-bom:${COMPOSE_BOM}"))`,
        '    implementation("androidx.compose.ui:ui")',
        '    implementation("androidx.compose.material3:material3")',
      ].join('\n')
    );
  if (!root || !app)
    return [manual(appBuildGradle, "the Gradle files aren't those of the template")];
  return [
    {
      name: path.join('android', 'build.gradle'),
      platform: 'android',
      file: rootBuildGradle,
      status: 'change',
      before: rootBefore,
      next: root,
      note: 'Compose compiler plugin',
    },
    {
      name: path.join('android', 'app', 'build.gradle'),
      platform: 'android',
      file: appBuildGradle,
      status: 'change',
      before: appBefore,
      next: app,
      note: 'Jetpack Compose',
    },
  ];
}

/**
 * The text with a line after the first line the pattern matches; undefined when none does.
 * @param {string} text
 * @param {RegExp} pattern
 * @param {(indent: string) => string} line gets the indentation of the line found
 * @returns {string | undefined}
 */
function insertAfterLine(text, pattern, line) {
  const found = pattern.exec(text);
  if (!found) return undefined;
  const end = found.index + found[0].length;
  const indent = /^\s*/.exec(found[0])?.[0].replace(/\n/g, '') ?? '';
  return text.slice(0, end) + '\n' + line(indent) + text.slice(end);
}

/**
 * @param {string | undefined} file
 * @param {string} note
 * @returns {FileChange}
 */
function manual(file, note) {
  return {
    name: 'build.gradle',
    platform: 'android',
    ...(file && { file }),
    status: 'manual',
    note: `${note}, see below`,
    howTo: HOW_TO,
  };
}

module.exports = { composeChanges, COMPOSE_BOM };
