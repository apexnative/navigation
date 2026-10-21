// @ts-check
'use strict';

const {
  constantName,
  kotlinIdentifier,
  kotlinString,
  memberName,
  swiftIdentifier,
  swiftString,
  typeName,
} = require('./names');

/** @typedef {import('./read').Model} Model */
/** @typedef {import('./read').Field} Field */
/** @typedef {import('./read').CommandScreenModel} CommandScreenModel */

const SHOW = [
  [
    'push',
    'Shows a screen of the app over this one in its stack; returns the id of the screen shown.',
  ],
  ['showModal', 'Shows a screen of the app over everything, in a stack of its own.'],
  ['showOverlay', 'Shows a screen of the app over everything, without taking the place of any.'],
  ['showInColumn', 'Shows a screen of the app in a column of the split view this screen is in.'],
];

// Names a nested type of Swift can't have.
const RESERVED = new Set(['Type', 'Protocol', 'Self']);

/**
 * The name of a type of a screen's props where the generated code declares it: the reader's name, with `Value` after
 * it when it is a name Swift reserves or the name of the screen itself, which its types are nested in.
 * @param {CommandScreenModel} screen
 * @param {string} name
 */
function localType(screen, name) {
  return RESERVED.has(name) || name === typeName(screen.name) ? `${name}Value` : name;
}

/**
 * The typed commands in Swift: the screens of the app as an enum whose values carry the props a command gives, and
 * the commands of `ScreenNavigation` that take one, `try await screen.navigation.push(.details(title: "Item"))`. The
 * types of each screen's props are in a namespace of its name, `Screens.Details.Tab`. The types of Swift are written
 * in full, as a screen may have the name of one.
 * @param {CommandScreenModel[]} screens
 * @returns {string}
 */
function swiftCommands(screens) {
  const cases = screens.map((screen) => {
    const name = swiftIdentifier(memberName(screen.name));
    if (screen.fields.length === 0) return `  case ${name}`;
    const values = screen.fields.map(
      (field) =>
        `${swiftIdentifier(field.name)}: ${swiftType(field.type, screen)}${field.optional ? '? = nil' : ''}`
    );
    return `  case ${name}(${values.join(', ')})`;
  });
  const names = screens.map(
    (screen) =>
      `    case .${swiftIdentifier(memberName(screen.name))}: return ${swiftString(screen.name)}`
  );
  const props = screens.map((screen) => {
    const name = swiftIdentifier(memberName(screen.name));
    if (screen.fields.length === 0) return `    case .${name}: return [:]`;
    const bound = screen.fields.map((field) => swiftIdentifier(field.name)).join(', ');
    return `    case let .${name}(${bound}): return presentFields(${swiftFields(screen.fields, screen)})`;
  });
  const namespaces = screens
    .filter((screen) => screen.enums.length + screen.objects.length > 0)
    .map((screen) => {
      const enums = screen.enums.map(
        (item) =>
          `    enum ${localType(screen, item.name)}: Swift.String, CaseIterable {\n${item.values
            .map(
              (value) => `      case ${swiftIdentifier(memberName(value))} = ${swiftString(value)}`
            )
            .join('\n')}\n    }`
      );
      const objects = screen.objects.map((object) => {
        const members = object.fields.map(
          (field) =>
            `      let ${swiftIdentifier(field.name)}: ${swiftType(field.type, screen)}${field.optional ? '?' : ''}`
        );
        const parameters = object.fields.map(
          (field) =>
            `${swiftIdentifier(field.name)}: ${swiftType(field.type, screen)}${field.optional ? '? = nil' : ''}`
        );
        const assignments = object.fields.map(
          (field) => `        self.${field.name} = ${swiftIdentifier(field.name)}`
        );
        return `    struct ${localType(screen, object.name)} {
${members.join('\n')}

      init(${parameters.join(', ')}) {
${assignments.join('\n')}
      }

      var json: [Swift.String: Any] { presentFields(${swiftFields(object.fields, screen)}) }
    }`;
      });
      return `  /// The types of the props of \`${screen.name}\`.\n  enum ${typeName(screen.name)} {\n${[...enums, ...objects].join('\n\n')}\n  }`;
    });
  const show = SHOW.map(
    ([command, doc]) => `  /// ${doc}
  @discardableResult
  func ${command}(_ screen: Screens) async throws -> Swift.String {
    try await ${command}(screen.name, props: screen.props)
  }`
  );
  return `// MARK: - The screens of the app

/// The screens of the app with the props a command gives each, for the typed commands of \`ScreenNavigation\`:
/// \`try await screen.navigation.push(.${screens[0] ? memberName(screens[0].name) : 'home'})\`.
enum Screens {
${cases.join('\n')}

  /// The name of the screen in \`screens\`.
  var name: Swift.String {
    switch self {
${names.join('\n')}
    }
  }

  /// The props as JS gets them; a value that isn't there is left out.
  var props: [Swift.String: Any] {
    switch self {
${props.join('\n')}
    }
  }
}
${namespaces.length > 0 ? `\nextension Screens {\n${namespaces.join('\n\n')}\n}\n` : ''}
extension ScreenNavigation {
${show.join('\n\n')}
}

/// The fields that have a value.
private func presentFields(_ fields: [Swift.String: Any?]) -> [Swift.String: Any] {
  fields.compactMapValues { $0 }
}
`;
}

/**
 * The fields as a Swift dictionary literal of what JS gets.
 * @param {Field[]} fields
 * @param {CommandScreenModel} screen
 */
function swiftFields(fields, screen) {
  if (fields.length === 0) return '[:]';
  const entries = fields.map((field) => {
    const value = swiftIdentifier(field.name);
    const inner = swiftEncode(field.type, '$0', screen);
    // A plain optional goes as it is: nil is left out.
    const encoded = field.optional
      ? inner === '$0'
        ? value
        : `${value}.map { ${inner} }`
      : swiftEncode(field.type, value, screen);
    return `${swiftString(field.name)}: ${encoded}`;
  });
  return `[${entries.join(', ')}]`;
}

/**
 * @param {Model} model
 * @param {CommandScreenModel} screen
 * @returns {string}
 */
function swiftType(model, screen) {
  switch (model.kind) {
    case 'string':
      return 'Swift.String';
    case 'number':
      return model.int ? 'Swift.Int' : 'Swift.Double';
    case 'boolean':
      return 'Swift.Bool';
    case 'enum':
    case 'object':
      return `Screens.${typeName(screen.name)}.${localType(screen, model.name)}`;
    case 'array':
      return `[${swiftType(model.element, screen)}]`;
    case 'function':
      return 'Never';
  }
}

/**
 * The value JS gets for a value of Swift.
 * @param {Model} model
 * @param {string} value
 * @param {CommandScreenModel} screen
 * @returns {string}
 */
function swiftEncode(model, value, screen) {
  switch (model.kind) {
    case 'enum':
      return `${value}.rawValue`;
    case 'object':
      return `${value}.json`;
    case 'array': {
      const element = swiftEncode(model.element, '$0', screen);
      return element === '$0' ? value : `${value}.map { ${element} }`;
    }
    default:
      return value;
  }
}

/**
 * The typed commands in Kotlin: the screens of the app as a sealed class, an object for a screen without props and a
 * data class for one with them, with the types of its props inside, and the commands of `ScreenNavigation` that take
 * one, `navigation.push(Screens.Details(title = "Item"))`. The classes have no members of their own, so a prop may have
 * any name; their names and props for JS are extensions of the file, and the types of Kotlin are written in full, as a
 * screen may have the name of one.
 * @param {CommandScreenModel[]} screens
 * @returns {string}
 */
function kotlinCommands(screens) {
  const classes = screens.map((screen) => {
    const name = typeName(screen.name);
    if (screen.fields.length === 0) return `  object ${name} : Screens()`;
    const parameters = screen.fields.map(
      (field) =>
        `    val ${kotlinIdentifier(field.name)}: ${kotlinType(field.type, screen)}${field.optional ? '? = null' : ''},`
    );
    const enums = screen.enums.map(
      (item) => `    enum class ${localType(screen, item.name)}(val value: kotlin.String) {
${item.values.map((value) => `      ${constantName(value)}(${kotlinString(value)}),`).join('\n')}
    }`
    );
    const objects = screen.objects.map((object) => {
      const fields = object.fields.map(
        (field) =>
          `      val ${kotlinIdentifier(field.name)}: ${kotlinType(field.type, screen)}${field.optional ? '? = null' : ''},`
      );
      return `    data class ${localType(screen, object.name)}(
${fields.join('\n')}
    ) {
      fun json(): JSONObject = JSONObject(${kotlinFields(object.fields)})
    }`;
    });
    const nested = [...enums, ...objects];
    return `  data class ${name}(
${parameters.join('\n')}
  ) : Screens()${nested.length > 0 ? ` {\n${nested.join('\n\n')}\n  }` : ''}`;
  });
  const names = screens.map(
    (screen) => `    is Screens.${typeName(screen.name)} -> ${kotlinString(screen.name)}`
  );
  const props = screens.map(
    (screen) =>
      `    is Screens.${typeName(screen.name)} -> ${
        screen.fields.length === 0 ? 'emptyMap()' : kotlinFields(screen.fields)
      }`
  );
  const show = SHOW.map(
    ([command, doc]) => `/** ${doc} */
suspend fun ScreenNavigation.${command}(screen: Screens): kotlin.String =
  ${command}(screen.jsName, screen.jsProps)`
  );
  return `// The screens of the app

/**
 * The screens of the app with the props a command gives each, for the typed commands of [ScreenNavigation]:
 * \`navigation.push(Screens.${screens[0] ? typeName(screens[0].name) : 'Home'})\`.
 */
sealed class Screens {
${classes.join('\n\n')}
}

/** The name of the screen in \`screens\`. */
private val Screens.jsName: kotlin.String
  get() = when (this) {
${names.join('\n')}
  }

/** The props as JS gets them; a value that isn't there is left out. */
private val Screens.jsProps: kotlin.collections.Map<kotlin.String, kotlin.Any?>
  get() = when (this) {
${props.join('\n')}
  }

${show.join('\n\n')}

/** The fields that have a value. */
private fun presentFields(
  vararg fields: kotlin.Pair<kotlin.String, kotlin.Any?>
): kotlin.collections.Map<kotlin.String, kotlin.Any?> = fields.filter { it.second != null }.toMap()
`;
}

/**
 * The fields as a call of `presentFields` with what JS gets.
 * @param {Field[]} fields
 */
function kotlinFields(fields) {
  const entries = fields.map((field) => {
    const value = kotlinIdentifier(field.name);
    const inner = kotlinEncode(field.type, 'it');
    // A plain optional goes as it is: null is left out.
    const encoded = field.optional
      ? inner === 'it'
        ? value
        : `${value}?.let { ${inner} }`
      : kotlinEncode(field.type, value);
    return `${kotlinString(field.name)} to ${encoded}`;
  });
  return `presentFields(${entries.join(', ')})`;
}

/**
 * @param {Model} model
 * @param {CommandScreenModel} screen
 * @returns {string}
 */
function kotlinType(model, screen) {
  switch (model.kind) {
    case 'string':
      return 'kotlin.String';
    case 'number':
      return model.int ? 'kotlin.Int' : 'kotlin.Double';
    case 'boolean':
      return 'kotlin.Boolean';
    case 'enum':
    case 'object':
      return localType(screen, model.name);
    case 'array':
      return `kotlin.collections.List<${kotlinType(model.element, screen)}>`;
    case 'function':
      return 'kotlin.Nothing';
  }
}

/**
 * The value JS gets for a value of Kotlin.
 * @param {Model} model
 * @param {string} value
 * @returns {string}
 */
function kotlinEncode(model, value) {
  switch (model.kind) {
    case 'enum':
      return `${value}.value`;
    case 'object':
      return `${value}.json()`;
    case 'array': {
      const element = kotlinEncode(model.element, 'it');
      return element === 'it' ? `JSONArray(${value})` : `JSONArray(${value}.map { ${element} })`;
    }
    default:
      return value;
  }
}

module.exports = { swiftCommands, kotlinCommands };
