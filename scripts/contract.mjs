#!/usr/bin/env node
// The one table of the native contract that both platforms answer from. The error texts come from
// src/interfaces/contract/errors.json, what each command answers from src/interfaces/contract/commands.json, the names
// of the events and the keys of their payloads from the event types of src/interfaces/ (Events.ts,
// ComponentEvents.ts). This script writes the files iOS and Android compile from them;
// they are committed, so an app's build needs no Node. With --check it writes nothing and fails when a file differs
// from what the sources make (scripts/check.sh runs it).
//
// Usage: node scripts/contract.mjs [--check]

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const ANDROID = 'android/src/main/java/dev/apexnative/navigation';
const ERRORS_SOURCE = 'src/interfaces/contract/errors.json';
const COMMANDS_SOURCE = 'src/interfaces/contract/commands.json';
const SPEC_SOURCE = 'src/adapters/NativeANNTurboModule.ts';
const EVENT_SOURCES = ['src/interfaces/Events.ts', 'src/interfaces/ComponentEvents.ts'];
const NOTICE = 'Made by scripts/contract.mjs';

// Objective-C lines stay within 100 columns, Kotlin lines within 120.
const OBJC_WIDTH = 100;
const KOTLIN_WIDTH = 120;

/** The error texts by group, as errors.json has them, each param as `{ name, type }`. */
function readErrors() {
  const source = JSON.parse(fs.readFileSync(path.join(root, ERRORS_SOURCE), 'utf8'));
  return source.groups.map((group) => ({
    title: group.title,
    texts: group.texts.map((text) => {
      const params = text.params.map((param) =>
        typeof param === 'string' ? { name: param, type: 'string' } : param
      );
      const names = new Set(params.map((param) => param.name));
      for (const [, name] of text.text.matchAll(/\{(\w+)\}/g)) {
        if (!names.has(name))
          throw new Error(`${text.name}: the text names {${name}}, which is no param`);
      }
      return { ...text, params, platforms: text.platforms ?? ['ios', 'android'] };
    }),
  }));
}

// The text split into its literal parts and its params, in order.
function pieces(text) {
  return text.split(/(\{\w+\})/).map((part) => {
    const param = part.match(/^\{(\w+)\}$/);
    return param ? { param: param[1] } : { literal: part };
  });
}

// A string literal cut into parts that fit the width, at the spaces.
function cut(text, width) {
  const parts = [];
  let rest = text;
  while (rest.length > width) {
    const at = rest.lastIndexOf(' ', width);
    if (at <= 0) break;
    parts.push(rest.slice(0, at + 1));
    rest = rest.slice(at + 1);
  }
  parts.push(rest);
  return parts;
}

// A doc comment of `prefix` lines, wrapped to the width.
function docLines(doc, prefix, width) {
  const lines = [];
  let line = '';
  for (const word of doc.split(/\s+/)) {
    if (line && prefix.length + line.length + 1 + word.length > width) {
      lines.push(prefix + line);
      line = word;
    } else {
      line = line ? `${line} ${word}` : word;
    }
  }
  if (line) lines.push(prefix + line);
  return lines;
}

function objcSelector(text) {
  if (text.params.length === 0) return text.name;
  return text.params
    .map((param, index) => {
      const type = param.type === 'int' ? 'NSUInteger' : 'NSString *';
      return `${index === 0 ? text.name : param.name}:(${type})${param.name}`;
    })
    .join(' ');
}

function objcBody(text) {
  const parts = pieces(text.text);
  if (parts.every((part) => 'literal' in part))
    return `    return @"${objcEscape(text.text, false)}";`;
  const format = parts
    .map((part) => {
      if ('literal' in part) return objcEscape(part.literal, true);
      return text.params.find((param) => param.name === part.param).type === 'int' ? '%lu' : '%@';
    })
    .join('');
  const args = parts
    .filter((part) => 'param' in part)
    .map((part) =>
      text.params.find((param) => param.name === part.param).type === 'int'
        ? `(unsigned long)${part.param}`
        : part.param
    );
  const literal = cut(format, 76).map((part) => `@"${part}"`);
  const oneLine = `    return ANNText(${literal.join(' ')}, ${args.join(', ')});`;
  if (literal.length === 1 && oneLine.length <= OBJC_WIDTH) return oneLine;
  // The literal's parts one per line, the last one followed by the comma, then the arguments.
  const lines = literal.map(
    (part, index) => (index === 0 ? '    return ANNText(' : ' '.repeat(19)) + part
  );
  lines[lines.length - 1] += ',';
  return [...lines, `${' '.repeat(19)}${args.join(', ')});`].join('\n');
}

function objcEscape(text, format) {
  const escaped = text.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  return format ? escaped.replace(/%/g, '%%') : escaped;
}

function errorTextsHeader(groups) {
  const lines = [
    `// ${NOTICE} from ${ERRORS_SOURCE}: change that file and run the script again.`,
    '',
    '#import <Foundation/Foundation.h>',
    '',
    'NS_ASSUME_NONNULL_BEGIN',
    '',
    '/// The name of an exception whose reason is the whole text a command rejects with (one of the texts below), as it is:',
    "/// a maker deep in a command's work throws it where the command can't answer itself. Any other exception rejects",
    '/// with `couldNot:reason:`.',
    'FOUNDATION_EXPORT NSExceptionName const ANNCommandRejectionException;',
    '',
    '/// Every text the native side rejects a command with or logs for a command without a promise: one sentence per case,',
    '/// the same on iOS and Android, the values in single quotes. Other files take their texts from here. Any thread.',
    '@interface ANNErrorTexts : NSObject',
  ];
  for (const group of groups) {
    const texts = group.texts.filter((text) => text.platforms.includes('ios'));
    if (texts.length === 0) continue;
    lines.push('', `#pragma mark - ${group.title}`, '');
    for (const text of texts) {
      if (text.doc) lines.push(...docLines(text.doc, '/// ', OBJC_WIDTH + 20));
      lines.push(`+ (NSString *)${objcSelector(text)};`);
    }
  }
  lines.push(
    '',
    '/// An `ANNCommandRejectionException` with `text` as its reason: the command that runs rejects with `text` as it is.',
    '+ (NSException *)rejection:(NSString *)text;',
    '',
    '@end',
    '',
    'NS_ASSUME_NONNULL_END',
    ''
  );
  return lines.join('\n');
}

function errorTextsImplementation(groups) {
  const lines = [
    `// ${NOTICE} from ${ERRORS_SOURCE}: change that file and run the script again.`,
    '',
    '#import "ANNErrorTexts.h"',
    '',
    'NSExceptionName const ANNCommandRejectionException = @"ANNCommandRejectionException";',
    '',
    'static NSString *ANNText(NSString *format, ...) NS_FORMAT_FUNCTION(1, 2);',
    '',
    'static NSString *ANNText(NSString *format, ...) {',
    '    va_list arguments;',
    '    va_start(arguments, format);',
    '    NSString *text = [[NSString alloc] initWithFormat:format arguments:arguments];',
    '    va_end(arguments);',
    '    return text;',
    '}',
    '',
    '@implementation ANNErrorTexts',
  ];
  for (const group of groups) {
    const texts = group.texts.filter((text) => text.platforms.includes('ios'));
    if (texts.length === 0) continue;
    lines.push('', `#pragma mark - ${group.title}`);
    for (const text of texts) {
      lines.push('', `+ (NSString *)${objcSelector(text)} {`, objcBody(text), '}');
    }
  }
  lines.push(
    '',
    '+ (NSException *)rejection:(NSString *)text {',
    '    return [NSException exceptionWithName:ANNCommandRejectionException reason:text userInfo:nil];',
    '}',
    '',
    '@end',
    ''
  );
  return lines.join('\n');
}

function kotlinString(text) {
  const parts = pieces(text);
  return parts
    .map((part, index) => {
      if ('literal' in part)
        return part.literal.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$');
      const next = parts[index + 1];
      return next && 'literal' in next && /^\w/.test(next.literal)
        ? `\${${part.param}}`
        : `$${part.param}`;
    })
    .join('');
}

function kotlinFunction(text) {
  const params = text.params.map(
    (param) => `${param.name}: ${param.type === 'int' ? 'Int' : 'String'}`
  );
  const head = `    fun ${text.name}(${params.join(', ')}) =`;
  const value = `"${kotlinString(text.text)}"`;
  const lines = text.doc ? docLines(text.doc, '', KOTLIN_WIDTH - 11).map((line) => line) : [];
  const doc =
    lines.length === 0
      ? []
      : lines.length === 1 && lines[0].length + 11 <= KOTLIN_WIDTH
        ? [`    /** ${lines[0]} */`]
        : ['    /**', ...docLines(text.doc, '     * ', KOTLIN_WIDTH), '     */'];
  if (`${head} ${value}`.length <= KOTLIN_WIDTH) return [...doc, `${head} ${value}`];
  if (`        ${value}`.length <= KOTLIN_WIDTH) return [...doc, head, `        ${value}`];
  const parts = cut(kotlinString(text.text), KOTLIN_WIDTH - 14).map((part) => `"${part}"`);
  return [
    ...doc,
    head,
    ...parts.map((part, index) => `        ${part}${index < parts.length - 1 ? ' +' : ''}`),
  ];
}

function errorTextsKotlin(groups) {
  const lines = [
    `// ${NOTICE} from ${ERRORS_SOURCE}: change that file and run the script again.`,
    '',
    'package dev.apexnative.navigation.react',
    '',
    '/**',
    ' * Every text the native side rejects a command with or writes to the log for a command without a promise: one function',
    ' * per case, the same words as iOS. Other code takes its texts from here. JavaScript prefixes them with `[navigation]',
    ' * <command>:`.',
    ' */',
    'internal object ErrorTexts {',
  ];
  let first = true;
  for (const group of groups) {
    const texts = group.texts.filter((text) => text.platforms.includes('android'));
    if (texts.length === 0) continue;
    if (!first) lines.push('');
    first = false;
    lines.push(`    // ${group.title}.`);
    for (const text of texts) lines.push('', ...kotlinFunction(text));
  }
  lines.push(
    '',
    '    /** The reason of [couldNot] for an exception: its message, else the name of its class. */',
    '    fun reasonOf(e: Throwable): String =',
    '        e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName.ifEmpty { e.javaClass.name }',
    '}',
    ''
  );
  return lines.join('\n');
}

/**
 * The events as the types of src/interfaces/ give them, by name (`CommandCompleted` for `CommandCompletedEvent`), and
 * the object types their fields hold (`ScreenRegionRect`, `NavigationStateChangedScreen` for an inline one). An event
 * whose JSDoc starts with `(iOS…)` without Android is iOS only, and so is such a field.
 */
function readEvents() {
  const files = EVENT_SOURCES.map((file) => path.join(root, file));
  const program = ts.createProgram(files, { strict: true, noEmit: true });
  const checker = program.getTypeChecker();
  const entries = new Map();
  const iosOnly = (doc) => {
    const platform = doc.match(/^\(([^)]*)\)/);
    return Boolean(platform && /iOS|iPadOS/.test(platform[1]) && !/Android/.test(platform[1]));
  };
  const add = (name, type, event, holder) => {
    if (entries.has(name)) return;
    const entry = { name, event, holder, ios: event?.ios ?? false, fields: [] };
    entries.set(name, entry);
    for (const property of checker.getPropertiesOfType(type)) {
      // `event` holds the event's own name in the made files.
      if (property.name === 'event') throw new Error(`${name}: a field can't be named 'event'`);
      const declaration = property.valueDeclaration ?? property.declarations?.[0];
      const doc = ts.displayPartsToString(property.getDocumentationComment(checker));
      entry.fields.push({
        key: property.name,
        optional: (property.flags & ts.SymbolFlags.Optional) !== 0,
        ios: entry.ios || iosOnly(doc),
      });
      let value = checker.getNonNullableType(
        checker.getTypeOfSymbolAtLocation(property, declaration)
      );
      if (checker.isArrayType(value)) value = checker.getTypeArguments(value)[0];
      if (!(value.flags & ts.TypeFlags.Object) || value.getCallSignatures().length > 0) continue;
      if (checker.getPropertiesOfType(value).length === 0) continue;
      const symbolName = value.getSymbol()?.getName();
      const nested =
        !symbolName || symbolName === '__type' ? name + capitalize(property.name) : symbolName;
      add(nested, value, undefined, `${entry.event?.name ?? entry.name}.${property.name}`);
    }
  };
  for (const file of files) {
    const source = program.getSourceFile(file);
    for (const statement of source.statements) {
      if (!ts.isInterfaceDeclaration(statement) && !ts.isTypeAliasDeclaration(statement)) continue;
      const exported = statement.modifiers?.some(
        (modifier) => modifier.kind === ts.SyntaxKind.ExportKeyword
      );
      const typeName = statement.name.text;
      if (!exported || !typeName.endsWith('Event')) continue;
      const symbol = checker.getSymbolAtLocation(statement.name);
      const doc = ts.displayPartsToString(symbol.getDocumentationComment(checker));
      const name = typeName.slice(0, -'Event'.length);
      add(name, checker.getDeclaredTypeOfSymbol(symbol), {
        name: `ANN.${name}`,
        ios: iosOnly(doc),
      });
    }
  }
  return [...entries.values()].sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));
}

// What an entry's comment calls it: the event, or the object a field of an event holds.
function title(entry) {
  return entry.event ? `\`${entry.event.name}\`` : `The object of \`${entry.holder}\``;
}

function capitalize(text) {
  return text[0].toUpperCase() + text.slice(1);
}

function upperSnake(text) {
  return text.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toUpperCase();
}

// What a field's comment says: whether the field may be missing, and whether only iOS sends it.
function fieldNote(field, entry) {
  const notes = [];
  if (field.optional) notes.push('Optional');
  if (field.ios && !entry.ios) notes.push('iOS only');
  return notes.join(', ');
}

const EVENTS_NOTICE = `${NOTICE} from the event types of src/interfaces/: change them and run the script again.`;

function eventContractHeader(entries) {
  const lines = [
    `// ${EVENTS_NOTICE}`,
    '',
    '#import <Foundation/Foundation.h>',
    '',
    'NS_ASSUME_NONNULL_BEGIN',
    '',
    '/// The names of the events `ANN.<Name>` (`event`) and the keys of their payloads, as the types of `src/interfaces/`',
    '/// give them; what each field means is its JSDoc there. The object types a field holds have keys of their own. The',
    '/// senders take every name and key from here: `ANNEventContract.CommandCompleted.commandId`.',
    'FOUNDATION_EXPORT const struct ANNEventContractTable {',
  ];
  for (const entry of entries) {
    lines.push(`    /// ${title(entry)}${entry.ios ? ' (iOS)' : ''}.`);
    lines.push('    struct {');
    if (entry.event) lines.push('        __unsafe_unretained NSString *event;');
    for (const field of entry.fields) {
      const note = fieldNote(field, entry);
      if (note) lines.push(`        /// ${note}.`);
      lines.push(`        __unsafe_unretained NSString *${field.key};`);
    }
    lines.push(`    } ${entry.name};`);
  }
  lines.push(
    '} ANNEventContract;',
    '',
    '/// The name of every event, `ANN.<Name>`.',
    'FOUNDATION_EXPORT NSArray<NSString *> *ANNEventContractNames(void);',
    '',
    'NS_ASSUME_NONNULL_END',
    ''
  );
  return lines.join('\n');
}

function eventContractImplementation(entries) {
  const lines = [
    `// ${EVENTS_NOTICE}`,
    '',
    '#import "ANNEventContract.h"',
    '',
    'const struct ANNEventContractTable ANNEventContract = {',
  ];
  for (const entry of entries) {
    const values = [
      ...(entry.event ? [`.event = @"${entry.event.name}"`] : []),
      ...entry.fields.map((field) => `.${field.key} = @"${field.key}"`),
    ];
    const oneLine = `    .${entry.name} = {${values.join(', ')}},`;
    if (oneLine.length <= OBJC_WIDTH) {
      lines.push(oneLine);
    } else {
      lines.push(
        `    .${entry.name} =`,
        '        {',
        ...values.map((value) => `            ${value},`),
        '        },'
      );
    }
  }
  lines.push('};', '', 'NSArray<NSString *> *ANNEventContractNames(void) {', '    return @[');
  for (const entry of entries) {
    if (entry.event) lines.push(`        ANNEventContract.${entry.name}.event,`);
  }
  lines.push('    ];', '}', '');
  return lines.join('\n');
}

function eventContractKotlin(entries) {
  const lines = [
    `// ${EVENTS_NOTICE}`,
    '',
    'package dev.apexnative.navigation.react.events',
    '',
    '/**',
    ' * The names of the events `ANN.<Name>` ([EVENT]) and the keys of their payloads, as the types of `src/interfaces/` give',
    ' * them; what each field means is its JSDoc there. The object types a field holds have keys of their own. The sender',
    ' * and the builders of payloads take every name and key from here: `EventContract.CommandCompleted.COMMAND_ID`. The',
    ' * events and fields only iOS sends are not here.',
    ' */',
    'internal object EventContract {',
  ];
  const android = entries.filter((entry) => !entry.ios);
  android.forEach((entry, index) => {
    if (index > 0) lines.push('');
    lines.push(`    /** ${title(entry)}. */`);
    lines.push(`    object ${entry.name} {`);
    if (entry.event) lines.push(`        const val EVENT = "${entry.event.name}"`);
    for (const field of entry.fields.filter((each) => !each.ios)) {
      const note = fieldNote(field, entry);
      if (note) lines.push(`        /** ${note}. */`);
      lines.push(`        const val ${upperSnake(field.key)} = "${field.key}"`);
    }
    lines.push('    }');
  });
  lines.push('}', '');
  return lines.join('\n');
}

/**
 * The commands with a promise as commands.json lists them, checked against the async methods of the TurboModule's
 * spec: each method has one entry, and each entry is a method.
 */
function readCommands() {
  const source = JSON.parse(fs.readFileSync(path.join(root, COMMANDS_SOURCE), 'utf8'));
  const specFile = path.join(root, SPEC_SOURCE);
  const spec = ts.createSourceFile(
    specFile,
    fs.readFileSync(specFile, 'utf8'),
    ts.ScriptTarget.Latest
  );
  const asyncMethods = new Set();
  for (const statement of spec.statements) {
    if (!ts.isInterfaceDeclaration(statement) || statement.name.text !== 'Spec') continue;
    for (const member of statement.members) {
      // A method, or a property that holds a function.
      const type = ts.isMethodSignature(member)
        ? member.type
        : ts.isPropertySignature(member) && member.type && ts.isFunctionTypeNode(member.type)
          ? member.type.type
          : undefined;
      if (!type) continue;
      if (/^Promise\b/.test(type.getText(spec))) asyncMethods.add(member.name.getText(spec));
    }
  }
  const names = new Set();
  for (const command of source.commands) {
    if (names.has(command.name))
      throw new Error(`${COMMANDS_SOURCE}: '${command.name}' is there twice`);
    names.add(command.name);
    if (!asyncMethods.has(command.name))
      throw new Error(`${COMMANDS_SOURCE}: '${command.name}' is no async method of ${SPEC_SOURCE}`);
    for (const field of ['state', 'completed']) {
      if (typeof command[field] !== 'boolean')
        throw new Error(`${COMMANDS_SOURCE}: '${command.name}' needs '${field}': true or false`);
    }
    if (typeof command.resolves !== 'string' || command.resolves.trim() === '')
      throw new Error(
        `${COMMANDS_SOURCE}: '${command.name}' needs 'resolves', what it resolves with`
      );
    if (command.resolves.includes('*/'))
      throw new Error(`${COMMANDS_SOURCE}: the 'resolves' of '${command.name}' can't hold '*/'`);
    if (command.completed && !command.state)
      throw new Error(
        `${COMMANDS_SOURCE}: '${command.name}' sends ANN.CommandCompleted without the state`
      );
  }
  for (const method of asyncMethods) {
    if (!names.has(method))
      throw new Error(`${COMMANDS_SOURCE}: the async method '${method}' has no entry`);
  }
  return source.commands;
}

const COMMANDS_NOTICE = `${NOTICE} from ${COMMANDS_SOURCE}: change that file and run the script again.`;

function commandContractHeader() {
  return [
    `// ${COMMANDS_NOTICE}`,
    '',
    '#import <Foundation/Foundation.h>',
    '',
    'NS_ASSUME_NONNULL_BEGIN',
    '',
    '/// Whether the navigation state goes before the answer of the command `name`: every command that may change what',
    '/// shows. Any thread.',
    'FOUNDATION_EXPORT BOOL ANNCommandReportsState(NSString *name);',
    '',
    '/// Whether `ANN.CommandCompleted` follows the answer of the command `name`: every command that shows or dismisses.',
    '/// Any thread.',
    'FOUNDATION_EXPORT BOOL ANNCommandSendsCompleted(NSString *name);',
    '',
    'NS_ASSUME_NONNULL_END',
    '',
  ].join('\n');
}

function objcNameSet(name, commands) {
  return [
    `static NSSet<NSString *> *${name}(void) {`,
    '    static NSSet<NSString *> *names;',
    '    static dispatch_once_t once;',
    '    dispatch_once(&once, ^{',
    '      names = [NSSet setWithArray:@[',
    ...commands.map((command) => `          @"${command.name}",`),
    '      ]];',
    '    });',
    '    return names;',
    '}',
  ];
}

function commandContractImplementation(commands) {
  const lines = [`// ${COMMANDS_NOTICE}`, '', '#import "ANNCommandContract.h"', ''];
  lines.push('// What each command resolves with:');
  for (const command of commands) {
    lines.push(
      ...docLines(`\`${command.name}\`: ${command.resolves}.`, '// - ', OBJC_WIDTH).map(
        (line, index) => (index === 0 ? line : line.replace(/^\/\/ - /, '//   '))
      )
    );
  }
  lines.push(
    '',
    ...objcNameSet(
      'ANNStateCommandNames',
      commands.filter((command) => command.state)
    ),
    '',
    ...objcNameSet(
      'ANNCompletedCommandNames',
      commands.filter((command) => command.completed)
    ),
    '',
    'BOOL ANNCommandReportsState(NSString *name) {',
    '    return [ANNStateCommandNames() containsObject:name];',
    '}',
    '',
    'BOOL ANNCommandSendsCompleted(NSString *name) {',
    '    return [ANNCompletedCommandNames() containsObject:name];',
    '}',
    ''
  );
  return lines.join('\n');
}

function commandContractKotlin(commands) {
  const set = (name, doc, chosen) => [
    `    /** ${doc} */`,
    `    val ${name}: Set<String> = setOf(`,
    ...chosen.map((command) => `        "${command.name}",`),
    '    )',
  ];
  return [
    `// ${COMMANDS_NOTICE}`,
    '',
    'package dev.apexnative.navigation.react',
    '',
    '/**',
    ' * What the commands with a promise answer, the same table as iOS. What each resolves with:',
    ...commands.flatMap((command) =>
      docLines(`\`${command.name}\`: ${command.resolves}.`, ' * - ', KOTLIN_WIDTH).map(
        (line, index) => (index === 0 ? line : line.replace(/^ \* - /, ' *   '))
      )
    ),
    ' */',
    'internal object CommandContract {',
    ...set(
      'STATE',
      'The commands whose answer comes after the navigation state: every one that may change what shows.',
      commands.filter((command) => command.state)
    ),
    '',
    ...set(
      'COMPLETED',
      'The commands that send `ANN.CommandCompleted` after their answer: every one that shows or dismisses.',
      commands.filter((command) => command.completed)
    ),
    '}',
    '',
  ].join('\n');
}

const errors = readErrors();
const commands = readCommands();
const events = readEvents();
const outputs = {
  'ios/Contract/ANNErrorTexts.h': errorTextsHeader(errors),
  'ios/Contract/ANNErrorTexts.mm': errorTextsImplementation(errors),
  [`${ANDROID}/react/ErrorTexts.kt`]: errorTextsKotlin(errors),
  'ios/Contract/ANNCommandContract.h': commandContractHeader(),
  'ios/Contract/ANNCommandContract.mm': commandContractImplementation(commands),
  [`${ANDROID}/react/CommandContract.kt`]: commandContractKotlin(commands),
  'ios/Core/ANNEventContract.h': eventContractHeader(events),
  'ios/Core/ANNEventContract.mm': eventContractImplementation(events),
  [`${ANDROID}/react/events/EventContract.kt`]: eventContractKotlin(events),
};

if (process.argv.includes('--check')) {
  const stale = Object.entries(outputs).filter(([file, content]) => {
    const target = path.join(root, file);
    return !fs.existsSync(target) || fs.readFileSync(target, 'utf8') !== content;
  });
  for (const [file] of stale)
    console.error(`error: ${file} differs from its source: run node scripts/contract.mjs`);
  process.exitCode = stale.length ? 1 : 0;
} else {
  for (const [file, content] of Object.entries(outputs))
    fs.writeFileSync(path.join(root, file), content);
  console.log(`wrote ${Object.keys(outputs).length} files`);
}
