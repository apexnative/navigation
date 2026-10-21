// @ts-check
'use strict';
/* eslint-disable no-bitwise -- TypeScript's type and symbol flags are bit masks. */

const path = require('node:path');
const { memberName, typeName } = require('./names');

/** @typedef {typeof import('typescript')} TS */
/** @typedef {import('typescript').Type} Type */
/** @typedef {import('typescript').Node} Node */
/** @typedef {import('typescript').TypeChecker} TypeChecker */

/**
 * The type of a prop, as Swift and Kotlin get it.
 * - number: `int` when the prop has the JSDoc tag `@int`;
 * - enum: a union of strings;
 * - object: a nested struct / data class, by `name` in the screen's types;
 * - function: a function of the props, which the native side calls in JS; `result` is what it returns (resolves with,
 *   for a promise), null for nothing.
 * @typedef {{ kind: 'string' }
 *   | { kind: 'number'; int: boolean }
 *   | { kind: 'boolean' }
 *   | { kind: 'enum'; name: string }
 *   | { kind: 'array'; element: Model }
 *   | { kind: 'object'; name: string }
 *   | { kind: 'function'; parameters: { name: string; type: Model }[]; result: Model | null }} Model
 */
/** @typedef {{ name: string; type: Model; optional: boolean; doc?: string }} Field */
/** @typedef {{ name: string; fields: Field[] }} ObjectType */
/** @typedef {{ name: string; values: string[] }} EnumType */
/**
 * A native screen of the app: its name on the native side, its props (those of the command, with what `useProps`
 * returns over them) and the types they use. `file` is where `nativeScreen(...)` is, from the root.
 * @typedef {{
 *   nativeName: string;
 *   file: string;
 *   fields: Field[];
 *   objects: ObjectType[];
 *   enums: EnumType[];
 * }} NativeScreenModel
 */
/** @typedef {{ file: string; line: number; message: string }} CodegenError */
/**
 * A screen of the app as the typed commands of native code show it: its name in `screens`, the props a command gives
 * it, and their types. No functions: a command from native code can't give one; `functions` names the optional
 * ones, which a command from JS may give.
 * @typedef {{
 *   name: string;
 *   native: boolean;
 *   fields: Field[];
 *   functions: string[];
 *   objects: ObjectType[];
 *   enums: EnumType[];
 * }} CommandScreenModel
 */

const PACKAGE = '@apexnative/navigation';
// Built-in objects with no plain-data form on the native side.
const NOT_DATA = new Set([
  'Date',
  'Map',
  'Set',
  'WeakMap',
  'WeakSet',
  'Promise',
  'RegExp',
  'Error',
  'ArrayBuffer',
]);

/**
 * The program of the app's code, from its navigation module, with its tsconfig's options. `overrides` are files as a
 * command is about to write them, read in place of what is on disk (a new screen, a changed Navigation.ts).
 * @param {TS} ts
 * @param {string} root
 * @param {string} navigation
 * @param {Map<string, string>} [overrides] texts by absolute path
 */
function createProgram(ts, root, navigation, overrides = new Map()) {
  /** @type {import('typescript').CompilerOptions} */
  let options = {
    allowJs: true,
    jsx: ts.JsxEmit.ReactJSX,
    strict: true,
    module: ts.ModuleKind.ESNext,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
    target: ts.ScriptTarget.ESNext,
  };
  const configFile = ts.findConfigFile(root, ts.sys.fileExists, 'tsconfig.json');
  if (configFile) {
    const { config } = ts.readConfigFile(configFile, ts.sys.readFile);
    const parsed = ts.parseJsonConfigFileContent(config ?? {}, ts.sys, path.dirname(configFile));
    options = { ...options, ...parsed.options };
  }
  const finalOptions = { ...options, noEmit: true, skipLibCheck: true };
  const host = ts.createCompilerHost(finalOptions, true);
  if (overrides.size > 0) {
    const { fileExists, readFile, getSourceFile, directoryExists } = host;
    const overridden = (/** @type {string} */ file) => overrides.get(path.resolve(file));
    host.fileExists = (file) => overridden(file) !== undefined || fileExists.call(host, file);
    host.readFile = (file) => overridden(file) ?? readFile.call(host, file);
    host.directoryExists = (dir) =>
      [...overrides.keys()].some((file) => file.startsWith(path.resolve(dir) + path.sep)) ||
      (directoryExists ? directoryExists.call(host, dir) : true);
    host.getSourceFile = (file, languageVersion, onError, shouldCreate) => {
      const text = overridden(file);
      return text !== undefined
        ? ts.createSourceFile(file, text, languageVersion, true)
        : getSourceFile.call(host, file, languageVersion, onError, shouldCreate);
    };
  }
  return ts.createProgram({ rootNames: [navigation], options: finalOptions, host });
}

/**
 * The native screens of the app: every `nativeScreen(...)` of the navigation's package in its code.
 * @param {TS} ts
 * @param {import('typescript').Program} program
 * @param {string} root
 * @returns {{ screens: NativeScreenModel[]; errors: CodegenError[] }}
 */
function readNativeScreens(ts, program, root) {
  const checker = program.getTypeChecker();
  /** @type {NativeScreenModel[]} */
  const screens = [];
  /** @type {CodegenError[]} */
  const errors = [];
  for (const source of program.getSourceFiles()) {
    if (source.isDeclarationFile || source.fileName.includes('/node_modules/')) continue;
    const local = nativeScreenName(ts, source);
    if (!local) continue;
    const visit = (/** @type {Node} */ node) => {
      if (
        ts.isCallExpression(node) &&
        ts.isIdentifier(node.expression) &&
        node.expression.text === local
      ) {
        const screen = readScreen(ts, checker, root, source, node, errors);
        if (screen) screens.push(screen);
      }
      ts.forEachChild(node, visit);
    };
    visit(source);
  }
  return { screens, errors };
}

/**
 * The name the file gives `nativeScreen` of the navigation's package; none when it doesn't import it.
 * @param {TS} ts
 * @param {import('typescript').SourceFile} source
 */
function nativeScreenName(ts, source) {
  for (const statement of source.statements) {
    if (!ts.isImportDeclaration(statement) || !ts.isStringLiteral(statement.moduleSpecifier))
      continue;
    if (statement.moduleSpecifier.text !== PACKAGE) continue;
    const bindings = statement.importClause?.namedBindings;
    if (!bindings || !ts.isNamedImports(bindings)) continue;
    const element = bindings.elements.find(
      (item) => (item.propertyName ?? item.name).text === 'nativeScreen'
    );
    if (element) return element.name.text;
  }
  return undefined;
}

/**
 * One `nativeScreen(name, { useProps })`: the props of its type argument with what `useProps` returns over them.
 * @param {TS} ts
 * @param {TypeChecker} checker
 * @param {string} root
 * @param {import('typescript').SourceFile} source
 * @param {import('typescript').CallExpression} call
 * @param {CodegenError[]} errors
 * @returns {NativeScreenModel | undefined}
 */
function readScreen(ts, checker, root, source, call, errors) {
  const file = path.relative(root, source.fileName);
  const fail = (/** @type {Node} */ node, /** @type {string} */ message) => {
    const { line } = source.getLineAndCharacterOfPosition(node.getStart(source));
    errors.push({ file, line: line + 1, message });
  };
  const nameArgument = call.arguments[0];
  if (!nameArgument || !ts.isStringLiteralLike(nameArgument)) {
    fail(call, "The native name of a screen is a string literal: nativeScreen('Profile')");
    return undefined;
  }
  const nativeName = nameArgument.text;
  /** @type {Map<string, ObjectType>} */
  const objects = new Map();
  /** @type {Map<string, EnumType>} */
  const enums = new Map();
  const reader = createReader(ts, checker, (node, message) => fail(node, message), objects, enums);

  /** @type {Map<string, Field>} */
  const fields = new Map();
  // The props of the command: the type argument, else the type nativeScreen infers.
  const propsType = call.typeArguments?.[0]
    ? checker.getTypeFromTypeNode(call.typeArguments[0])
    : propsOfResult(ts, checker, call);
  if (propsType)
    for (const field of reader.fields(propsType, call, false)) fields.set(field.name, field);

  // What useProps returns goes over them.
  const definition = call.arguments[1];
  if (definition && ts.isObjectLiteralExpression(definition)) {
    const useProps = definition.properties.find(
      (property) =>
        property.name && ts.isIdentifier(property.name) && property.name.text === 'useProps'
    );
    if (useProps) {
      const signature = checker.getTypeAtLocation(useProps).getCallSignatures()[0];
      if (signature) {
        for (const field of reader.fields(signature.getReturnType(), useProps, true))
          fields.set(field.name, field);
      }
    }
  }
  return {
    nativeName,
    file,
    fields: [...fields.values()],
    objects: [...objects.values()],
    enums: [...enums.values()],
  };
}

/**
 * The props type nativeScreen inferred: that of the `props` carrier of what it returns.
 * @param {TS} ts
 * @param {TypeChecker} checker
 * @param {import('typescript').CallExpression} call
 * @returns {Type | undefined}
 */
function propsOfResult(ts, checker, call) {
  const carrier = checker.getTypeAtLocation(call).getProperty('props');
  if (!carrier) return undefined;
  return checker.getNonNullableType(checker.getTypeOfSymbolAtLocation(carrier, call));
}

/**
 * Reads types into models, naming their objects and enums once per screen.
 * @param {TS} ts
 * @param {TypeChecker} checker
 * @param {(node: Node, message: string) => void} fail
 * @param {Map<string, ObjectType>} objects
 * @param {Map<string, EnumType>} enums
 */
function createReader(ts, checker, fail, objects, enums) {
  /**
   * Reports a type with no native form; the field goes.
   * @param {Node} node
   * @param {string} message
   * @returns {undefined}
   */
  const failed = (node, message) => {
    fail(node, message);
    return undefined;
  };

  /**
   * The fields of an object type, `functions` when it may have functions (the props themselves).
   * @param {Type} type
   * @param {Node} at where an error points when a field has no declaration
   * @param {boolean} functions
   * @returns {Field[]}
   */
  function fields(type, at, functions) {
    /** @type {Field[]} */
    const result = [];
    for (const property of checker.getPropertiesOfType(type)) {
      const declaration = property.valueDeclaration ?? property.declarations?.[0];
      const node = declaration ?? at;
      const propertyType = checker.getTypeOfSymbolAtLocation(property, node);
      const optional =
        (property.flags & ts.SymbolFlags.Optional) !== 0 || hasNullish(ts, propertyType);
      const int = declaration ? hasTag(ts, declaration, 'int') : false;
      const model = modelOf(checker.getNonNullableType(propertyType), node, property.name, {
        functions,
        int,
      });
      if (!model) continue;
      const doc = ts.displayPartsToString(property.getDocumentationComment(checker)).trim();
      result.push({ name: property.name, type: model, optional, ...(doc && { doc }) });
    }
    return result;
  }

  /**
   * @param {Type} type
   * @param {Node} node
   * @param {string} name the field's name, for the names of its types
   * @param {{ functions: boolean; int: boolean }} options
   * @returns {Model | undefined}
   */
  function modelOf(type, node, name, { functions, int }) {
    const flags = type.flags;
    if (flags & (ts.TypeFlags.Any | ts.TypeFlags.Unknown)) {
      return failed(node, `${name}: any and unknown have no native type; give the prop a type`);
    }
    if (type.isUnion()) {
      const members = type.types.filter(
        (member) => !(member.flags & (ts.TypeFlags.Undefined | ts.TypeFlags.Null))
      );
      if (members.every((member) => member.isStringLiteral())) {
        const enumName = declaredName(type.aliasSymbol?.name ?? typeName(name));
        const values = members.map(
          (member) => /** @type {import('typescript').StringLiteralType} */ (member).value
        );
        enums.set(enumName, { name: enumName, values });
        return { kind: 'enum', name: enumName };
      }
      if (members.every((member) => member.flags & ts.TypeFlags.BooleanLike))
        return { kind: 'boolean' };
      if (members.length === 1) return modelOf(members[0], node, name, { functions, int });
      return failed(node, `${name}: a union of different types has no native type`);
    }
    if (flags & ts.TypeFlags.StringLike) return { kind: 'string' };
    if (flags & ts.TypeFlags.NumberLike) return { kind: 'number', int };
    if (flags & ts.TypeFlags.BooleanLike) return { kind: 'boolean' };
    if (checker.isTupleType(type))
      return failed(node, `${name}: tuples have no native type; use an object`);
    if (checker.isArrayType(type)) {
      const [element] = checker.getTypeArguments(
        /** @type {import('typescript').TypeReference} */ (type)
      );
      const model = element && modelOf(element, node, `${name} item`, { functions: false, int });
      return model && { kind: 'array', element: model };
    }
    const signatures = type.getCallSignatures();
    if (signatures.length > 0) {
      if (!functions) {
        return failed(
          node,
          `${name}: only the props themselves can be functions, not what is in them`
        );
      }
      return functionModel(signatures[0], node, name);
    }
    const symbol = type.getSymbol();
    if (symbol && (NOT_DATA.has(symbol.name) || symbol.declarations?.some(ts.isClassDeclaration))) {
      return failed(
        node,
        `${name}: ${symbol.name} has no plain-data form; pass a string or numbers`
      );
    }
    if (checker.getIndexInfosOfType(type).length > 0) {
      return failed(
        node,
        `${name}: objects with any keys (Record, index signatures) have no native type`
      );
    }
    if (flags & ts.TypeFlags.Object) {
      const named = symbol && !symbol.name.startsWith('__') ? symbol.name : undefined;
      const objectName = declaredName(type.aliasSymbol?.name ?? named ?? typeName(name));
      if (!objects.has(objectName)) {
        // Named before its fields are read, so a type that holds itself ends.
        objects.set(objectName, { name: objectName, fields: [] });
        objects.set(objectName, { name: objectName, fields: fields(type, node, false) });
      }
      return { kind: 'object', name: objectName };
    }
    return failed(node, `${name}: ${checker.typeToString(type)} has no native type`);
  }

  /**
   * @param {import('typescript').Signature} signature
   * @param {Node} node
   * @param {string} name
   * @returns {Model | undefined}
   */
  function functionModel(signature, node, name) {
    const parameters = [];
    for (const parameter of signature.getParameters()) {
      const declaration = parameter.valueDeclaration ?? node;
      const parameterType = checker.getNonNullableType(
        checker.getTypeOfSymbolAtLocation(parameter, declaration)
      );
      const model = modelOf(parameterType, declaration, `${name} ${parameter.name}`, {
        functions: false,
        int: hasTag(ts, declaration, 'int'),
      });
      if (!model) return undefined;
      parameters.push({ name: parameter.name, type: model });
    }
    let result = signature.getReturnType();
    const awaited = checker.getAwaitedType(result);
    if (awaited) result = awaited;
    if (result.flags & (ts.TypeFlags.Void | ts.TypeFlags.Undefined | ts.TypeFlags.Never)) {
      return { kind: 'function', parameters, result: null };
    }
    const resultModel = modelOf(checker.getNonNullableType(result), node, `${name} result`, {
      functions: false,
      int: false,
    });
    return resultModel && { kind: 'function', parameters, result: resultModel };
  }

  return { fields };
}

/**
 * @param {TS} ts
 * @param {Type} type
 */
function hasNullish(ts, type) {
  return (
    type.isUnion() &&
    type.types.some((member) => member.flags & (ts.TypeFlags.Undefined | ts.TypeFlags.Null))
  );
}

/**
 * The name of a type of the props as the generated code declares it: a name Swift reserves for a nested type gets
 * `Value` after it, `type: 'primary' | 'secondary'` becomes `TypeValue`.
 * @param {string} name
 */
function declaredName(name) {
  return ['Type', 'Protocol', 'Self'].includes(name) ? `${name}Value` : name;
}

/**
 * Whether a declaration has a JSDoc tag, such as `@int`.
 * @param {TS} ts
 * @param {Node} node
 * @param {string} tag
 */
function hasTag(ts, node, tag) {
  return ts.getJSDocTags(node).some((item) => item.tagName.text === tag);
}

/**
 * The screens of `screens` and of the groups, React and native, with the props a command gives each. A screen whose
 * props have no native form, or a function the command has to give, is left out, with why: native code still opens
 * it by its name.
 * @param {TS} ts
 * @param {import('typescript').Program} program
 * @param {string} navigation the module of the app's navigation
 * @returns {{ screens: CommandScreenModel[]; skipped: { name: string; native: boolean; reason: string }[] }}
 */
function readCommandScreens(ts, program, navigation) {
  const checker = program.getTypeChecker();
  const source = program.getSourceFile(navigation);
  /** @type {CommandScreenModel[]} */
  const screens = [];
  /** @type {{ name: string; native: boolean; reason: string }[]} */
  const skipped = [];
  const config = source && navigationConfig(ts, source);
  if (!config) return { screens, skipped };

  /** @type {{ name: string; type: Type; at: Node }[]} */
  const entries = [];
  const add = (/** @type {Type} */ object, /** @type {Node} */ at) => {
    for (const symbol of checker.getPropertiesOfType(object)) {
      entries.push({ name: symbol.name, type: checker.getTypeOfSymbolAtLocation(symbol, at), at });
    }
  };
  const screensValue = configValue(ts, config, 'screens');
  if (screensValue) add(checker.getTypeAtLocation(screensValue), screensValue);
  const groupsValue = configValue(ts, config, 'groups');
  if (groupsValue) {
    for (const group of checker.getPropertiesOfType(checker.getTypeAtLocation(groupsValue))) {
      const groupScreens = checker
        .getTypeOfSymbolAtLocation(group, groupsValue)
        .getProperty('screens');
      if (groupScreens)
        add(checker.getTypeOfSymbolAtLocation(groupScreens, groupsValue), groupsValue);
    }
  }

  // The names a screen takes in Swift and Kotlin: two screens may not end with one.
  /** @type {Map<string, string>} */
  const taken = new Map();
  for (const { name, type, at } of entries) {
    const generated = [typeName(name), memberName(name)];
    const other = generated.map((item) => taken.get(item)).find(Boolean);
    if (other) {
      skipped.push({
        name,
        native: type.getProperty('nativeName') !== undefined,
        reason: `its name in Swift and Kotlin is that of ${other}`,
      });
      continue;
    }
    for (const item of generated) taken.set(item, name);
    /** @type {string[]} */
    const errors = [];
    /** @type {Map<string, ObjectType>} */
    const objects = new Map();
    /** @type {Map<string, EnumType>} */
    const enums = new Map();
    const reader = createReader(
      ts,
      checker,
      (_node, message) => errors.push(message),
      objects,
      enums
    );
    const native = type.getProperty('nativeName') !== undefined;
    const props = propsOfEntry(ts, checker, type, at, 3);
    const read = props ? reader.fields(props, at, true) : [];
    const required = read.find((field) => field.type.kind === 'function' && !field.optional);
    if (errors.length > 0 || required) {
      skipped.push({
        name,
        native,
        reason: required
          ? `its prop ${required.name} is a function, which native code can't give`
          : errors[0],
      });
      continue;
    }
    screens.push({
      name,
      native,
      fields: read.filter((field) => field.type.kind !== 'function'),
      functions: read.filter((field) => field.type.kind === 'function').map((field) => field.name),
      objects: [...objects.values()],
      enums: [...enums.values()],
    });
  }
  return { screens, skipped };
}

/**
 * The props of an entry of `screens`, as the commands type them: those of a native screen, of the component of a
 * screen definition or of a component itself, or of the default export a lazy screen loads.
 * @param {TS} ts
 * @param {TypeChecker} checker
 * @param {Type} type
 * @param {Node} at
 * @param {number} depth how many lazy modules it still opens
 * @returns {Type | undefined}
 */
function propsOfEntry(ts, checker, type, at, depth) {
  const carrier = type.getProperty('nativeName') && type.getProperty('props');
  if (carrier) return checker.getNonNullableType(checker.getTypeOfSymbolAtLocation(carrier, at));
  const component = type.getProperty('component');
  const callable = component ? checker.getTypeOfSymbolAtLocation(component, at) : type;
  // ComponentType is a union of a function and a class component: the function one has the props as its parameter.
  const members = callable.isUnion() ? callable.types : [callable];
  const signature = members.map((member) => member.getCallSignatures()[0]).find(Boolean);
  const parameter = signature?.getParameters()[0];
  if (parameter) return checker.getTypeOfSymbolAtLocation(parameter, at);
  const load = type.getProperty('load');
  if (load && depth > 0) {
    const loaded = checker.getTypeOfSymbolAtLocation(load, at).getCallSignatures()[0];
    const module = loaded && checker.getAwaitedType(loaded.getReturnType());
    const exported = module?.getProperty('default');
    return exported
      ? propsOfEntry(ts, checker, checker.getTypeOfSymbolAtLocation(exported, at), at, depth - 1)
      : undefined;
  }
  return undefined;
}

/**
 * The object of the `createNavigation({ ... })` call in the file.
 * @param {TS} ts
 * @param {import('typescript').SourceFile} source
 * @returns {import('typescript').ObjectLiteralExpression | undefined}
 */
function navigationConfig(ts, source) {
  /** @type {import('typescript').ObjectLiteralExpression | undefined} */
  let config;
  const visit = (/** @type {Node} */ node) => {
    if (
      ts.isCallExpression(node) &&
      ts.isIdentifier(node.expression) &&
      node.expression.text === 'createNavigation' &&
      node.arguments[0] &&
      ts.isObjectLiteralExpression(node.arguments[0])
    ) {
      config = node.arguments[0];
    }
    if (!config) ts.forEachChild(node, visit);
  };
  visit(source);
  return config;
}

/**
 * The expression a key of the config is written with: its value, or the name of a shorthand.
 * @param {TS} ts
 * @param {import('typescript').ObjectLiteralExpression} config
 * @param {string} key
 * @returns {Node | undefined}
 */
function configValue(ts, config, key) {
  for (const property of config.properties) {
    if (ts.isPropertyAssignment(property) && property.name.getText() === key) {
      return property.initializer;
    }
    if (ts.isShorthandPropertyAssignment(property) && property.name.text === key) {
      return property.name;
    }
  }
  return undefined;
}

module.exports = { createProgram, readNativeScreens, readCommandScreens };
