#import "NativeProfileViewController.h"

static UIColor *ColorFromHex(NSString *hex) {
  NSString *clean = [[hex stringByReplacingOccurrencesOfString:@"#" withString:@""] uppercaseString];
  unsigned int rgb = 0;
  if (clean.length != 6 || ![[NSScanner scannerWithString:clean] scanHexInt:&rgb]) {
    return UIColor.systemIndigoColor;
  }
  return [UIColor colorWithRed:((rgb >> 16) & 0xFF) / 255.0
                         green:((rgb >> 8) & 0xFF) / 255.0
                          blue:(rgb & 0xFF) / 255.0
                         alpha:1.0];
}

@implementation NativeProfileViewController {
  ANNNativeScreenContext *_context;
  NSDictionary *_props;
  UIColor *_accent;
  NSInteger _counter;
  UILabel *_counterLabel;
  UIStackView *_stack;
}

- (instancetype)initWithContext:(ANNNativeScreenContext *)context {
  if (self = [super initWithNibName:nil bundle:nil]) {
    _context = context;
    _props = context.props;
    _accent = ColorFromHex([_props[@"accentColor"] isKindOfClass:NSString.class] ? _props[@"accentColor"] : @"");
    _counter = [_props[@"initialCount"] respondsToSelector:@selector(integerValue)] ? [_props[@"initialCount"] integerValue] : 0;
  }
  return self;
}

- (void)viewDidLoad {
  [super viewDidLoad];
  self.view.backgroundColor = UIColor.systemGroupedBackgroundColor;

  // Native bar button, added straight to the UINavigationItem managed by ApexNavigation's stack.
  self.navigationItem.rightBarButtonItem =
      [[UIBarButtonItem alloc] initWithImage:[UIImage systemImageNamed:@"info.circle"]
                                       style:UIBarButtonItemStylePlain
                                      target:self
                                      action:@selector(showNativeAlert)];

  UIScrollView *scroll = [UIScrollView new];
  scroll.translatesAutoresizingMaskIntoConstraints = NO;
  scroll.alwaysBounceVertical = YES;
  [self.view addSubview:scroll];

  _stack = [UIStackView new];
  _stack.axis = UILayoutConstraintAxisVertical;
  _stack.spacing = 10;
  _stack.translatesAutoresizingMaskIntoConstraints = NO;
  [scroll addSubview:_stack];

  [NSLayoutConstraint activateConstraints:@[
    [scroll.topAnchor constraintEqualToAnchor:self.view.topAnchor],
    [scroll.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor],
    [scroll.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
    [scroll.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
    [_stack.topAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.topAnchor constant:24],
    [_stack.bottomAnchor constraintEqualToAnchor:scroll.contentLayoutGuide.bottomAnchor constant:-32],
    // Safe area (not the frame): on iPadOS 26 the detail column extends under the floating sidebar.
    [_stack.leadingAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.leadingAnchor constant:20],
    [_stack.trailingAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.trailingAnchor constant:-20],
  ]];

  [self buildContent];
}

#pragma mark - Layout

- (void)buildContent {
  NSString *name = [_props[@"name"] isKindOfClass:NSString.class] ? _props[@"name"] : @"Unknown";

  [_stack addArrangedSubview:[self avatarWithName:name]];
  [_stack addArrangedSubview:[self label:name size:24 weight:UIFontWeightBold color:UIColor.labelColor centered:YES]];
  NSString *role = [_props[@"role"] isKindOfClass:NSString.class] ? _props[@"role"] : @"";
  [_stack addArrangedSubview:[self label:role size:15 weight:UIFontWeightRegular color:UIColor.secondaryLabelColor centered:YES]];
  [_stack addArrangedSubview:[self chipsRow]];

  [_stack addArrangedSubview:[self sectionTitle:@"Props received from JS"]];
  UILabel *dump = [self label:[self propsDump] size:13 weight:UIFontWeightRegular color:UIColor.labelColor centered:NO];
  dump.font = [UIFont monospacedSystemFontOfSize:13 weight:UIFontWeightRegular];
  [_stack addArrangedSubview:[self card:dump]];

  [_stack addArrangedSubview:[self sectionTitle:@"Native state"]];
  _counterLabel = [self label:@"" size:18 weight:UIFontWeightBold color:_accent centered:YES];
  [self updateCounter];
  [_stack addArrangedSubview:[self card:_counterLabel]];
  UIStackView *counterRow = [[UIStackView alloc] initWithArrangedSubviews:@[
    [self button:@"−1" action:@selector(decrement)], [self button:@"+1" action:@selector(increment)]
  ]];
  counterRow.spacing = 8;
  counterRow.distribution = UIStackViewDistributionFillEqually;
  [_stack addArrangedSubview:counterRow];

  [_stack addArrangedSubview:[self sectionTitle:@"Native → JS"]];
  [_stack addArrangedSubview:[self button:@"Send counter to JS" action:@selector(sendCounter)]];
  [_stack addArrangedSubview:[self button:@"Open a React screen from here" action:@selector(openReactScreen)]];
  [_stack addArrangedSubview:[self button:@"Close this screen (JS pops it)" action:@selector(requestClose)]];

  [_stack addArrangedSubview:[self sectionTitle:@"Pure native actions"]];
  [_stack addArrangedSubview:[self button:@"Push a plain UIKit view controller" action:@selector(pushPlainViewController)]];
  [_stack addArrangedSubview:[self button:@"Show UIAlertController" action:@selector(showNativeAlert)]];
}

- (UIView *)avatarWithName:(NSString *)name {
  NSMutableString *initials = [NSMutableString string];
  for (NSString *part in [name componentsSeparatedByString:@" "]) {
    if (part.length > 0 && initials.length < 2) {
      [initials appendString:[[part substringToIndex:1] uppercaseString]];
    }
  }
  UILabel *avatar = [self label:initials size:30 weight:UIFontWeightBold color:UIColor.whiteColor centered:YES];
  avatar.backgroundColor = _accent;
  avatar.layer.cornerRadius = 44;
  avatar.layer.masksToBounds = YES;
  avatar.translatesAutoresizingMaskIntoConstraints = NO;
  [avatar.widthAnchor constraintEqualToConstant:88].active = YES;
  [avatar.heightAnchor constraintEqualToConstant:88].active = YES;

  UIView *wrapper = [UIView new];
  [wrapper addSubview:avatar];
  [NSLayoutConstraint activateConstraints:@[
    [avatar.centerXAnchor constraintEqualToAnchor:wrapper.centerXAnchor],
    [avatar.topAnchor constraintEqualToAnchor:wrapper.topAnchor],
    [avatar.bottomAnchor constraintEqualToAnchor:wrapper.bottomAnchor],
  ]];
  return wrapper;
}

- (UIView *)chipsRow {
  NSMutableArray<UIView *> *chips = [NSMutableArray array];
  if ([_props[@"isPremium"] boolValue]) {
    [chips addObject:[self chip:@"★ Premium"]];
  }
  NSArray *tags = [_props[@"tags"] isKindOfClass:NSArray.class] ? _props[@"tags"] : @[];
  for (id tag in tags) {
    [chips addObject:[self chip:[tag description]]];
  }
  UIStackView *row = [[UIStackView alloc] initWithArrangedSubviews:chips];
  row.spacing = 6;
  UIView *wrapper = [UIView new];
  row.translatesAutoresizingMaskIntoConstraints = NO;
  [wrapper addSubview:row];
  [NSLayoutConstraint activateConstraints:@[
    [row.centerXAnchor constraintEqualToAnchor:wrapper.centerXAnchor],
    [row.topAnchor constraintEqualToAnchor:wrapper.topAnchor],
    [row.bottomAnchor constraintEqualToAnchor:wrapper.bottomAnchor],
  ]];
  return wrapper;
}

- (UILabel *)chip:(NSString *)text {
  UILabel *chip = [self label:[NSString stringWithFormat:@"  %@  ", text] size:12 weight:UIFontWeightSemibold color:_accent centered:YES];
  chip.backgroundColor = [_accent colorWithAlphaComponent:0.15];
  chip.layer.cornerRadius = 11;
  chip.layer.masksToBounds = YES;
  [chip.heightAnchor constraintEqualToConstant:22].active = YES;
  return chip;
}

- (NSString *)propsDump {
  NSMutableArray<NSString *> *lines = [NSMutableArray array];
  for (NSString *key in [_props.allKeys sortedArrayUsingSelector:@selector(compare:)]) {
    id value = _props[key];
    NSString *type = @"object";
    if ([value isKindOfClass:NSString.class]) type = @"string";
    else if (value == (id)kCFBooleanTrue || value == (id)kCFBooleanFalse) type = @"boolean";
    else if ([value isKindOfClass:NSNumber.class]) type = @"number";
    else if ([value isKindOfClass:NSArray.class]) type = @"array";
    NSString *rendered = [value isKindOfClass:NSArray.class] ? [(NSArray *)value componentsJoinedByString:@", "]
                       : [type isEqualToString:@"boolean"] ? ([value boolValue] ? @"true" : @"false")
                                                            : [value description];
    [lines addObject:[NSString stringWithFormat:@"%@ (%@): %@", key, type, rendered]];
  }
  return [lines componentsJoinedByString:@"\n"];
}

- (UILabel *)sectionTitle:(NSString *)title {
  UILabel *label = [self label:title.uppercaseString size:12 weight:UIFontWeightBold color:UIColor.secondaryLabelColor centered:NO];
  [label.heightAnchor constraintGreaterThanOrEqualToConstant:34].active = YES;
  return label;
}

- (UIView *)card:(UIView *)content {
  UIView *card = [UIView new];
  card.backgroundColor = UIColor.secondarySystemGroupedBackgroundColor;
  card.layer.cornerRadius = 14;
  content.translatesAutoresizingMaskIntoConstraints = NO;
  [card addSubview:content];
  [NSLayoutConstraint activateConstraints:@[
    [content.topAnchor constraintEqualToAnchor:card.topAnchor constant:14],
    [content.bottomAnchor constraintEqualToAnchor:card.bottomAnchor constant:-14],
    [content.leadingAnchor constraintEqualToAnchor:card.leadingAnchor constant:16],
    [content.trailingAnchor constraintEqualToAnchor:card.trailingAnchor constant:-16],
  ]];
  return card;
}

- (UIButton *)button:(NSString *)title action:(SEL)action {
  UIButtonConfiguration *config = [UIButtonConfiguration filledButtonConfiguration];
  config.title = title;
  config.baseBackgroundColor = _accent;
  config.cornerStyle = UIButtonConfigurationCornerStyleLarge;
  UIButton *button = [UIButton buttonWithConfiguration:config primaryAction:nil];
  [button addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
  [button.heightAnchor constraintEqualToConstant:48].active = YES;
  return button;
}

- (UILabel *)label:(NSString *)text size:(CGFloat)size weight:(UIFontWeight)weight color:(UIColor *)color centered:(BOOL)centered {
  UILabel *label = [UILabel new];
  label.text = text;
  label.numberOfLines = 0;
  label.font = [UIFont systemFontOfSize:size weight:weight];
  label.textColor = color;
  label.textAlignment = centered ? NSTextAlignmentCenter : NSTextAlignmentNatural;
  return label;
}

#pragma mark - Actions

- (void)increment {
  _counter++;
  [self updateCounter];
}

- (void)decrement {
  _counter--;
  [self updateCounter];
}

- (void)updateCounter {
  _counterLabel.text = [NSString stringWithFormat:@"Counter: %ld", (long)_counter];
}

// A function of the props (`useProps`), which runs in JS.
- (void)sendCounter {
  [_context call:@"sendCounter" arguments:@[ @(_counter) ] completion:nil];
}

// A command of the navigation from this screen.
- (void)openReactScreen {
  [_context navigate:@"push" arguments:@[ @"Details" ] completion:nil];
}

// Back in its stack, or out of its modal; the first screen of a tab stays.
- (void)requestClose {
  ANNNativeScreenContext *context = _context;
  [context navigate:@"pop"
          arguments:@[]
         completion:^(id popped, NSError *notInStack) {
           if (!notInStack) {
             return;
           }
           [context navigate:@"dismiss"
                   arguments:@[]
                  completion:^(id dismissed, NSError *notInModal) {
                    if (notInModal) {
                      [context call:@"log" arguments:@[ @"Close: the first screen of its stack stays" ] completion:nil];
                    }
                  }];
         }];
}

- (void)pushPlainViewController {
  UIViewController *plain = [UIViewController new];
  plain.title = @"Plain UIKit";
  plain.view.backgroundColor = UIColor.systemBackgroundColor;
  UILabel *label = [self label:@"This UIViewController was pushed with\n-[UINavigationController pushViewController:]\ninside ApexNavigation's native stack."
                          size:16 weight:UIFontWeightMedium color:UIColor.labelColor centered:YES];
  label.translatesAutoresizingMaskIntoConstraints = NO;
  [plain.view addSubview:label];
  [NSLayoutConstraint activateConstraints:@[
    [label.centerYAnchor constraintEqualToAnchor:plain.view.centerYAnchor],
    [label.leadingAnchor constraintEqualToAnchor:plain.view.leadingAnchor constant:24],
    [label.trailingAnchor constraintEqualToAnchor:plain.view.trailingAnchor constant:-24],
  ]];
  [self.navigationController pushViewController:plain animated:YES];
}

- (void)showNativeAlert {
  UIDevice *device = UIDevice.currentDevice;
  [self alertWithTitle:@"Native alert"
               message:[NSString stringWithFormat:@"%@ %@\nCounter = %ld", device.systemName, device.systemVersion, (long)_counter]];
}

- (void)alertWithTitle:(NSString *)title message:(NSString *)message {
  UIAlertController *alert = [UIAlertController alertControllerWithTitle:title
                                                                 message:message
                                                          preferredStyle:UIAlertControllerStyleAlert];
  [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
  [self presentViewController:alert animated:YES completion:nil];
}

@end
