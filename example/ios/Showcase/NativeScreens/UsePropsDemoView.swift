import SwiftUI

/// The native screen of sections 18.4–18.7 in SwiftUI: props from the app (`useProps`), functions of the props that run
/// in JS, and commands of the navigation from the screen. AppDelegate registers it as `UsePropsDemoScreen`.
struct UsePropsDemoView: View {
  @ObservedObject var screen: ScreenProps
  @State private var items: [String] = []
  @State private var failure: String?

  var body: some View {
    List {
      Section("From the app") {
        row("Count", "\(screen.int("count"))")
        row("User", screen.string("user"))
        Button("+1") { run { try await screen.call("increment") } }
        Button("Sign in as Ann") { run { try await screen.call("signIn", "Ann") } }
        Button("Sign out") { run { try await screen.call("signOut") } }
      }
      Section("A function with a result") {
        ForEach(items, id: \.self) { Text($0) }
        if let failure {
          Text(failure).foregroundStyle(.red)
        }
        Button("Load again") { load("loadItems") }
        Button("Fail to load") { load("failToLoad") }
      }
      Section("Commands") {
        Button("Push Details") { run { try await screen.navigate("push", "Details") } }
        Button("Show Details as a modal") {
          run { try await screen.navigate("showModal", "Details", ["closeButton": true]) }
        }
        Button("Close") { close() }
      }
      if screen.has("backToShowcase") {
        Section("Native first") {
          Button("Back to the Showcase") { run { try await screen.call("backToShowcase") } }
        }
      }
    }
    .task { load("loadItems") }
    // What the screen sees of the store: each new count goes to the Journal.
    .onReceive(screen.$values.map { ($0["count"] as? NSNumber)?.intValue ?? 0 }.removeDuplicates().dropFirst()) {
      count in
      run { try await screen.call("log", "The screen sees count \(count)") }
    }
  }

  private func row(_ label: String, _ value: String) -> some View {
    HStack {
      Text(label)
      Spacer()
      Text(value).foregroundStyle(.secondary)
    }
  }

  // A function that returns the items, or one that fails: the screen shows what came back.
  private func load(_ function: String) {
    Task {
      do {
        let loaded = try await screen.call(function) as? [String] ?? []
        items = loaded
        failure = nil
        try await screen.call("log", "Loaded \(loaded.joined(separator: ", "))")
      } catch {
        items = []
        failure = error.localizedDescription
        _ = try? await screen.call("log", "Failed: \(error.localizedDescription)")
      }
    }
  }

  // Back in its stack, or out of its modal when it is the first screen of one.
  private func close() {
    Task {
      do {
        try await screen.navigate("pop")
      } catch {
        _ = try? await screen.navigate("dismiss")
      }
    }
  }

  private func run(_ action: @escaping () async throws -> Void) {
    Task { try? await action() }
  }
}
