// @ts-check
'use strict';

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { composeChanges, COMPOSE_BOM } = require('../gradle');

const FIXTURE = path.join(__dirname, '..', '__fixtures__', 'app-0.87', 'android');

function android() {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'apex-navigation-gradle-'));
  fs.cpSync(FIXTURE, dir, { recursive: true });
  return {
    rootBuildGradle: path.join(dir, 'build.gradle'),
    appBuildGradle: path.join(dir, 'app', 'build.gradle'),
  };
}

const project = (/** @type {Partial<import('../project').Project>} */ files) => ({
  root: '',
  typescript: true,
  ...files,
});

describe('Jetpack Compose in the app', () => {
  it("is set up as React Native's template has its Gradle files", () => {
    const files = android();
    const [root, app] = composeChanges(project(files));
    expect(root.next).toContain(
      '        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin")\n        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlinVersion")'
    );
    expect(app.next).toContain(
      'apply plugin: "org.jetbrains.kotlin.android"\napply plugin: "org.jetbrains.kotlin.plugin.compose"'
    );
    expect(app.next).toContain('android {\n    buildFeatures {\n        compose true\n    }\n');
    expect(app.next).toContain(
      `dependencies {\n    implementation(platform("androidx.compose:compose-bom:${COMPOSE_BOM}"))\n    implementation("androidx.compose.ui:ui")\n    implementation("androidx.compose.material3:material3")\n`
    );
  });

  it('is there already the second time', () => {
    const files = android();
    for (const change of composeChanges(project(files))) {
      if (change.file && change.next) fs.writeFileSync(change.file, change.next);
    }
    expect(composeChanges(project(files)).map(({ status }) => status)).toEqual(['done']);
  });

  it('is left to the user when the files are not as the template has them', () => {
    const files = android();
    fs.writeFileSync(files.appBuildGradle, 'plugins { id("com.android.application") }\n');
    const [change] = composeChanges(project(files));
    expect(change.status).toBe('manual');
    expect(change.howTo).toContain('apply plugin: "org.jetbrains.kotlin.plugin.compose"');
  });
});
