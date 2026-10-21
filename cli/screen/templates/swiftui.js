// @ts-check
'use strict';

/**
 * A native screen in SwiftUI: its props and the navigation from it come in `screen`.
 * @param {string} name such as `Profile`
 * @param {string} definition where the screen's props are, for the comment
 * @returns {string}
 */
function swiftUIScreen(name, definition) {
  return `import SwiftUI

/// The native screen \`${name}\`. Its props (from ${definition}) and the navigation from it come in \`screen\`; the view
/// renders again when the props change.
struct ${name}Screen: View {
  @ObservedObject var screen: NativeScreen<${name}Props>

  var body: some View {
    List {
      Text(screen.props.title ?? "${name}")
      Button("Close") {
        Task { try? await screen.navigation.pop() }
      }
    }
  }
}
`;
}

module.exports = { swiftUIScreen };
