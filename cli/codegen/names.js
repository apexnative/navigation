// @ts-check
'use strict';

// Words a name can't be as it is: Swift and Kotlin take them in backticks.
const SWIFT_KEYWORDS = new Set(
  (
    'associatedtype class deinit enum extension fileprivate func import init inout internal let open operator private ' +
    'precedencegroup protocol public rethrows static struct subscript typealias var break case catch continue default ' +
    'defer do else fallthrough for guard if in repeat return throw switch where while as false is nil self Self super ' +
    'throws true try Any Type'
  ).split(' ')
);
const KOTLIN_KEYWORDS = new Set(
  (
    'as break class continue do else false for fun if in interface is null object package return super this throw ' +
    'true try typealias typeof val var when while'
  ).split(' ')
);

/**
 * A name as a type is written: `user` → `User`, `sign-in` → `SignIn`.
 * @param {string} text
 */
function typeName(text) {
  const name = words(text)
    .map((word) => word[0].toUpperCase() + word.slice(1))
    .join('');
  // A name of Swift and Kotlin doesn't start with a digit.
  return /^[0-9]/.test(name) ? `_${name}` : name;
}

/**
 * A name as a property or a case is written: `sign-in` → `signIn`, `Large Title` → `largeTitle`.
 * @param {string} text
 */
function memberName(text) {
  const name = typeName(text);
  return name.startsWith('_') ? name : name[0].toLowerCase() + name.slice(1);
}

/**
 * A name in UPPER_SNAKE_CASE, as Kotlin writes enum constants.
 * @param {string} text
 */
function constantName(text) {
  const name = words(text)
    .flatMap((word) => word.split(/(?<=[a-z0-9])(?=[A-Z])/))
    .map((word) => word.toUpperCase())
    .join('_');
  return /^[0-9]/.test(name) ? `_${name}` : name;
}

/** @param {string} text */
function words(text) {
  const found = text.split(/[^A-Za-z0-9]+/).filter(Boolean);
  return found.length > 0 ? found : ['value'];
}

/**
 * A name Swift takes as it is, or in backticks.
 * @param {string} name
 */
function swiftIdentifier(name) {
  return SWIFT_KEYWORDS.has(name) ? `\`${name}\`` : name;
}

/**
 * A name Kotlin takes as it is, or in backticks.
 * @param {string} name
 */
function kotlinIdentifier(name) {
  return KOTLIN_KEYWORDS.has(name) ? `\`${name}\`` : name;
}

/**
 * A string literal for Swift source.
 * @param {string} text
 */
function swiftString(text) {
  return JSON.stringify(text).replace(/\\u([0-9a-f]{4})/gi, '\\u{$1}');
}

/**
 * A string literal for Kotlin source, where `$` starts a template.
 * @param {string} text
 */
function kotlinString(text) {
  return JSON.stringify(text).replace(/\$/g, '\\$');
}

module.exports = {
  typeName,
  memberName,
  constantName,
  swiftIdentifier,
  kotlinIdentifier,
  swiftString,
  kotlinString,
};
