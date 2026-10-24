---
paths:
  - 'ios/**/*.{h,m,mm,cpp}'
  - 'example/ios/**/*.{h,m,mm,swift}'
---

# Objective-C++

No formatter runs on these files; keep the style by hand. It is clang-format's LLVM style with 4 spaces and 100
columns.

## Format

- 4 spaces, no tabs. Code lines up to 100 columns, comments up to 120. Braces on the same line: `- (void)foo {`,
  `} else {`. `Type *name`, `(NSString *)`.
- `@property(nonatomic, strong)`, no space after `@property`; attributes in the order `nonatomic`, ownership,
  `readonly`, `nullable`. No `retain`.
- A block's body is indented 2 from the line with `^{`. Multi-line selectors align their colons.
- A final newline, no trailing whitespace.

## Names

- Every class, enum, C function and constant starts with `ANN`: `ANNPreviewInteraction`,
  `typedef NS_ENUM(NSInteger, ANNSideMenuSide)`, `static const CGFloat ANNSideMenuDefaultWidth`.
- One class per `.h`/`.mm` pair, named after it; `.mm`, not `.m`.
- Categories: the file `UIViewController+ANNOptions.h`, the category `(ANNOptions)`.
- No `get` prefix. Associated-object keys: `static const void *ANNXKey = &ANNXKey;`.

## Files

- `#import`, no include guards. The class's own header first, then the project's headers by bare name, then
  `<React/…>`, `<UIKit/…>`. A new folder goes into `headerFolders` of `ios/Package.swift`.
- A new header wraps its declarations in `NS_ASSUME_NONNULL_BEGIN` / `NS_ASSUME_NONNULL_END` and marks `nullable`
  what may be nil.
- Private state in the class extension or in `@implementation X { … }`, never in the header.
- Sections: `#pragma mark - <Protocol>`.

## Memory and threads

- In a block: `__weak ANNFoo *weakSelf = self;` (or `weakX`), and inside it a strong local named after its role. No
  `typeof(self)`.
- Everything on the main queue. A command starts with `ANNAssertMainQueue()`; entry points from JS move to it with
  `RCTExecuteOnMainQueue`. `@synchronized` only for state read from any thread.
- Newer OS features: `if (@available(iOS 26.0, *))`, and `API_AVAILABLE(ios(26.0))` on methods called only inside
  such a check. Nothing at or below the deployment target (15.1) needs a check.


## Comments

- `//` above the code, in full sentences, saying why rather than what; backticks for option paths. Header docs with
  `///`.
- No file headers of our own. A third-party file keeps its header; a change adds `//  Modified by <Name>, <year>.`
  below the copyright line.

## Errors and logs

- A command fails by rejecting its promise; the reason is a lowercase clause (JS adds `[navigation] <command>:`),
  as `ANNErrorTexts` lists them.
- Logs: `RCTLogWarn` / `RCTLogError(@"[ApexNavigation] …")`, never `NSLog`.

## Swift

- None in the library's core: Swift sees it through `NS_SWIFT_NAME` in the public headers. The Showcase's Swift
  (`example/ios/Showcase`) uses 2 spaces.

## Models

Our modules that stay show the house style; read the closest one before writing.

- UIKit glue with weak references in blocks, associated objects and availability checks:
  `ios/Layouts/Screen/ANNPreviewInteraction.{h,mm}`.
- A nullability-annotated header and state read from any thread: `ios/Layouts/Screen/ANNScreenEnvironment.{h,mm}`.
