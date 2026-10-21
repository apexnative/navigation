import ApexNavigation
import SwiftUI

/// A native screen written in SwiftUI. `screens` names it with `nativeScreen('SwiftUIProfileScreen')`; AppDelegate
/// registers it. Its context runs the functions of its props and the commands of the navigation in JS.
struct SwiftUIProfileView: View {
  let context: NativeScreenContext
  @State private var counter = 0

  private var name: String { context.props["name"] as? String ?? "SwiftUI screen" }
  private var role: String { context.props["role"] as? String ?? "" }
  private var tags: [String] { context.props["tags"] as? [String] ?? [] }
  private var accent: Color { Color(hex: context.props["accentColor"] as? String) ?? .accentColor }
  private var initials: String {
    name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined()
  }

  var body: some View {
    List {
      Section {
        VStack(spacing: 8) {
          Text(initials)
            .font(.largeTitle.bold())
            .foregroundStyle(.white)
            .frame(width: 88, height: 88)
            .background(accent, in: Circle())
          Text(name).font(.title2.bold())
          if !role.isEmpty {
            Text(role).foregroundStyle(.secondary)
          }
          if !tags.isEmpty {
            Text(tags.joined(separator: " · ")).font(.footnote).foregroundStyle(accent)
          }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
      }

      Section("Props received from JS") {
        ForEach(context.props.keys.sorted(), id: \.self) { key in
          HStack(alignment: .firstTextBaseline) {
            Text(key)
            Spacer()
            Text(describe(context.props[key]))
              .foregroundStyle(.secondary)
              .multilineTextAlignment(.trailing)
          }
          .font(.callout)
        }
        HStack {
          Text("componentId")
          Spacer()
          Text(context.componentId).foregroundStyle(.secondary)
        }
        .font(.callout)
      }

      Section("Native → JS") {
        Stepper("Counter: \(counter)", value: $counter)
        Button("Send the counter to JS") {
          Task { _ = try? await context.call("sendCounter", arguments: [counter]) }
        }
        Button("Open a React screen from here") {
          Task { _ = try? await context.navigate("push", arguments: ["Details"]) }
        }
        Button("Close", role: .destructive) { close() }
      }
    }
    .tint(accent)
  }

  /// A prop value as JS wrote it: booleans as true/false, arrays joined.
  private func describe(_ value: Any?) -> String {
    switch value {
    case let number as NSNumber where CFGetTypeID(number) == CFBooleanGetTypeID():
      return number.boolValue ? "true" : "false"
    case let array as [Any]:
      return array.map { describe($0) }.joined(separator: ", ")
    case let value?:
      return String(describing: value)
    case nil:
      return ""
    }
  }

  // Back in its stack, or out of its modal; the first screen of a tab stays.
  private func close() {
    Task {
      do {
        _ = try await context.navigate("pop", arguments: [])
      } catch {
        do {
          _ = try await context.navigate("dismiss", arguments: [])
        } catch {
          _ = try? await context.call("log", arguments: ["Close: the first screen of its stack stays"])
        }
      }
    }
  }
}

private extension Color {
  /// `#RRGGBB` from JS; nil when the string is missing or malformed.
  init?(hex: String?) {
    guard let hex, hex.hasPrefix("#"), hex.count == 7, let value = UInt32(hex.dropFirst(), radix: 16) else {
      return nil
    }
    self.init(
      red: Double((value >> 16) & 0xFF) / 255,
      green: Double((value >> 8) & 0xFF) / 255,
      blue: Double(value & 0xFF) / 255
    )
  }
}
