import ApexNavigation
import Combine

/// The props of a native screen for SwiftUI: they change while the screen is open (`useProps` in JS), and a view that
/// observes them renders again. The functions of the props and the commands of the navigation run in JS.
final class ScreenProps: ObservableObject {
  let context: NativeScreenContext
  @Published private(set) var values: [String: Any]

  init(_ context: NativeScreenContext) {
    self.context = context
    values = context.props
    context.onPropsChange = { [weak self] props in self?.values = props }
  }

  func string(_ key: String) -> String { values[key] as? String ?? "" }

  func int(_ key: String) -> Int { (values[key] as? NSNumber)?.intValue ?? 0 }

  /// Whether the props have the function.
  func has(_ function: String) -> Bool { context.functions.contains(function) }

  /// Runs a function of the props in JS; returns what it returned.
  @discardableResult
  func call(_ function: String, _ arguments: Any...) async throws -> Any? {
    try await context.call(function, arguments: arguments)
  }

  /// Runs a command of the navigation from the screen in JS.
  @discardableResult
  func navigate(_ command: String, _ arguments: Any...) async throws -> Any? {
    try await context.navigate(command, arguments: arguments)
  }
}
