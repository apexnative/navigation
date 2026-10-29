// @ts-check
'use strict';

const path = require('node:path');
const babel = require('@babel/core');

const acceptPlugin = require('../acceptPlugin');

/** @param {string} code */
function withAccept(code) {
  return babel.transformSync(code, {
    babelrc: false,
    configFile: false,
    plugins: [acceptPlugin],
    sourceType: 'module',
  })?.code;
}

describe('withApexNavigation', () => {
  beforeEach(() => {
    delete process.env.APEX_NAVIGATION_BABEL_TRANSFORMER;
    jest.resetModules();
  });

  it("puts the navigation's transformer in front of the one the app set", () => {
    const { withApexNavigation } = require('..');
    const config = withApexNavigation({
      projectRoot: '/app',
      transformer: { babelTransformerPath: '/app/svgTransformer.js', minifierPath: 'm' },
    });
    expect(config.transformer).toEqual({
      babelTransformerPath: require.resolve('../babelTransformer'),
      minifierPath: 'm',
    });
    expect(process.env.APEX_NAVIGATION_BABEL_TRANSFORMER).toBe('/app/svgTransformer.js');
  });

  it("goes in front of React Native's transformer by default, once", () => {
    const { withApexNavigation } = require('..');
    // The Showcase has React Native's packages, as an app has.
    const projectRoot = path.join(__dirname, '..', '..', 'example');
    const config = withApexNavigation(withApexNavigation({ projectRoot }));
    expect(config.transformer?.babelTransformerPath).toBe(require.resolve('../babelTransformer'));
    expect(process.env.APEX_NAVIGATION_BABEL_TRANSFORMER).toBe(
      require.resolve('@react-native/metro-babel-transformer', { paths: [projectRoot] })
    );
  });
});

describe("the navigation's Babel transformer", () => {
  const upstream = path.join(__dirname, '..', '__fixtures__', 'upstream.js');

  beforeEach(() => {
    process.env.APEX_NAVIGATION_BABEL_TRANSFORMER = upstream;
    jest.resetModules();
  });

  it('gives the app’s files `hot` and the accept plugin in development', () => {
    const transformer = require('../babelTransformer');
    const result = transformer.transform({
      filename: '/app/src/App.ts',
      options: { dev: true },
      plugins: ['one'],
      src: '',
    });
    expect(result.options.hot).toBe(true);
    expect(result.plugins).toEqual(['one', require('../acceptPlugin')]);
  });

  it("gives the packages' files `hot` without the plugin", () => {
    const transformer = require('../babelTransformer');
    const result = transformer.transform({
      filename: '/app/node_modules/lib/index.js',
      options: { dev: true },
      plugins: ['one'],
      src: '',
    });
    expect(result.options.hot).toBe(true);
    expect(result.plugins).toEqual(['one']);
  });

  it('changes nothing in a release build', () => {
    const transformer = require('../babelTransformer');
    const args = {
      filename: '/app/src/App.ts',
      options: { dev: false },
      plugins: ['one'],
      src: '',
    };
    expect(transformer.transform(args)).toBe(args);
  });

  it('has a cache key of its own', () => {
    expect(require('../babelTransformer').getCacheKey()).toBe('apexnative-navigation-1:upstream');
  });
});

describe('the accept plugin', () => {
  it('makes a module that calls createNavigation accept its own updates', () => {
    const code = withAccept(
      "import { createNavigation } from '@apexnative/navigation';\nexport const App = createNavigation({});"
    );
    expect(code).toContain('if (module.hot) {\n  module.hot.accept();\n}');
  });

  it('leaves any other module as it is', () => {
    const code = withAccept(
      "import { defineScreen } from '@apexnative/navigation';\nexport const Home = defineScreen({});"
    );
    expect(code).not.toContain('module.hot');
  });
});
