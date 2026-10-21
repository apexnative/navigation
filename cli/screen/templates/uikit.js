// @ts-check
'use strict';

/**
 * A native screen in UIKit: a view controller made with `screen`, which follows the props as they change.
 * @param {string} name such as `Profile`
 * @param {string} definition where the screen's props are, for the comment
 * @returns {string}
 */
function uiKitScreen(name, definition) {
  return `import Combine
import UIKit

/// The native screen \`${name}\`. Its props (from ${definition}) and the navigation from it come in \`screen\`.
final class ${name}ViewController: UIViewController {
  private let screen: NativeScreen<${name}Props>
  private let titleLabel = UILabel()
  private var updates: AnyCancellable?

  init(screen: NativeScreen<${name}Props>) {
    self.screen = screen
    super.init(nibName: nil, bundle: nil)
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not supported")
  }

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .systemBackground
    titleLabel.font = .preferredFont(forTextStyle: .title2)
    titleLabel.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(titleLabel)
    NSLayoutConstraint.activate([
      titleLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      titleLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
    ])
    // The props as they are, and again each time they change.
    updates = screen.$props.sink { [weak self] props in
      self?.titleLabel.text = props.title ?? "${name}"
    }
  }
}
`;
}

module.exports = { uiKitScreen };
