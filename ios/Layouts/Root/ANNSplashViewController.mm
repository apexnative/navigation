#import "ANNSplashViewController.h"
#import "ANNDefaultOptions.h"

// The legacy launch images by the screen's height in points: `Default` with its suffix, then the names an asset
// catalog's `LaunchImage` set compiles to. `UIImage` picks the scale.
static NSArray<NSString *> *ANNLegacyLaunchImageNames(CGFloat height) {
    NSMutableArray<NSString *> *names = [NSMutableArray new];
    NSDictionary<NSNumber *, NSArray<NSString *> *> *byHeight = @{
        @568 : @[ @"Default-568h", @"LaunchImage-700-568h" ],
        @667 : @[ @"Default-667h", @"LaunchImage-800-667h" ],
        @736 : @[ @"Default-736h", @"LaunchImage-800-Portrait-736h" ],
        @812 : @[ @"Default-812h", @"LaunchImage-1100-Portrait-2436h" ],
    };
    NSArray<NSString *> *sized = byHeight[@((NSInteger)round(height))];
    if (sized) {
        [names addObjectsFromArray:sized];
    }
    // The sets the Info.plist lists (`UILaunchImages`), for this size.
    NSArray *launchImages = [NSBundle.mainBundle objectForInfoDictionaryKey:@"UILaunchImages"];
    if ([launchImages isKindOfClass:NSArray.class]) {
        for (NSDictionary *entry in launchImages) {
            if (![entry isKindOfClass:NSDictionary.class]) {
                continue;
            }
            NSString *name = entry[@"UILaunchImageName"];
            NSString *size = entry[@"UILaunchImageSize"];
            if ([name isKindOfClass:NSString.class] && [size isKindOfClass:NSString.class] &&
                CGSizeFromString(size).height == round(height)) {
                [names addObject:name];
            }
        }
    }
    [names addObjectsFromArray:@[ @"Default", @"LaunchImage" ]];
    return names;
}

static NSDictionary *ANNDictionaryOrNil(id value) {
    return [value isKindOfClass:NSDictionary.class] ? value : nil;
}

static NSString *ANNStringOrNil(id value) {
    return [value isKindOfClass:NSString.class] && [value length] > 0 ? value : nil;
}

// The definition that a plural key (`UILaunchScreens`, `UILaunchStoryboards`) marks as its default.
static NSDictionary *ANNDefaultDefinition(id plural, NSString *definitionsKey,
                                          NSString *identifierKey, NSString *defaultKey) {
    NSDictionary *dictionary = ANNDictionaryOrNil(plural);
    NSArray *definitions = dictionary[definitionsKey];
    NSString *defaultIdentifier = ANNStringOrNil(dictionary[defaultKey]);
    if (![definitions isKindOfClass:NSArray.class] || !defaultIdentifier) {
        return nil;
    }
    for (id definition in definitions) {
        NSDictionary *entry = ANNDictionaryOrNil(definition);
        if ([entry[identifierKey] isEqual:defaultIdentifier]) {
            return entry;
        }
    }
    return nil;
}

// The launch screen that iOS builds from the Info.plist without a storyboard, or nil when it shows a storyboard. Of
// `UILaunchStoryboardName`, `UILaunchScreen`, the default of `UILaunchStoryboards` and the default of
// `UILaunchScreens`, the last one present wins (SplashBoard's `XBApplicationLaunchCompatibilityInfo`). A launch by a
// link that `UILaunchScreens` maps to a screen of its own still gets the default here.
static NSDictionary *ANNLaunchScreenConfiguration(NSBundle *bundle) {
    NSDictionary *screen =
        ANNDefaultDefinition([bundle objectForInfoDictionaryKey:@"UILaunchScreens"],
                             @"UILaunchScreenDefinitions", @"UILaunchScreenIdentifier",
                             @"UIDefaultLaunchScreen");
    if (screen) {
        return screen;
    }
    if (ANNDefaultDefinition([bundle objectForInfoDictionaryKey:@"UILaunchStoryboards"],
                             @"UILaunchStoryboardDefinitions", @"UILaunchStoryboardIdentifier",
                             @"UIDefaultLaunchStoryboard")) {
        return nil;
    }
    return ANNDictionaryOrNil([bundle objectForInfoDictionaryKey:@"UILaunchScreen"]);
}

@implementation ANNSplashViewController {
    UIViewController *_content;
    UIView *_contentView;
    NSDictionary *_launchScreen;
    // The parts of a launch screen built from `UILaunchScreen`, laid out once the safe area is known.
    UIImageView *_imageView;
    BOOL _imageRespectsSafeArea;
    UINavigationBar *_navigationBar;
    UIImageView *_navigationBarImageView;
    UIView *_bottomBar;
    UIImageView *_bottomBarImageView;
}

- (instancetype)initWithContent:(UIViewController *)content
                    contentView:(UIView *)contentView
                          blank:(BOOL)blank {
    if (self = [super initWithNibName:nil bundle:nil]) {
        _content = content;
        _contentView = contentView;
        _isBlankScreen = blank;
    }
    return self;
}

+ (instancetype)launchScreenCopyWithBounds:(CGRect)bounds {
    NSBundle *bundle = NSBundle.mainBundle;
    NSDictionary *launchScreen = ANNLaunchScreenConfiguration(bundle);
    if (launchScreen) {
        ANNSplashViewController *copy = [[self alloc] initWithContent:nil contentView:nil blank:NO];
        copy->_launchScreen = launchScreen;
        return copy;
    }
    NSString *storyboardName = [bundle objectForInfoDictionaryKey:@"UILaunchStoryboardName"];
    if ([storyboardName isKindOfClass:NSString.class] && storyboardName.length > 0) {
        UIViewController *initial = nil;
        @try {
            UIStoryboard *storyboard = [UIStoryboard storyboardWithName:storyboardName bundle:nil];
            initial = [storyboard instantiateInitialViewController];
        } @catch (NSException *exception) {
            initial = nil;
        }
        if (initial) {
            return [[self alloc] initWithContent:initial contentView:nil blank:NO];
        }
        if ([bundle pathForResource:storyboardName ofType:@"nib"]) {
            for (id object in [bundle loadNibNamed:storyboardName owner:nil options:nil]) {
                if ([object isKindOfClass:UIView.class]) {
                    return [[self alloc] initWithContent:nil contentView:object blank:NO];
                }
            }
        }
    }
    for (NSString *name in ANNLegacyLaunchImageNames(bounds.size.height)) {
        UIImage *image = [UIImage imageNamed:name];
        if (image) {
            UIImageView *imageView = [[UIImageView alloc] initWithImage:image];
            imageView.contentMode = UIViewContentModeScaleAspectFill;
            imageView.clipsToBounds = YES;
            return [[self alloc] initWithContent:nil contentView:imageView blank:NO];
        }
    }
    return nil;
}

+ (instancetype)blankScreen {
    return [[self alloc] initWithContent:nil contentView:nil blank:YES];
}

- (void)viewDidLoad {
    [super viewDidLoad];
    UIView *view = self.view;
    if (_isBlankScreen) {
        // Over the window's own color when the app gave it one.
        BOOL windowHasColor = ANNDefaultOptions.current.window.backgroundColor.hasValue;
        view.backgroundColor = windowHasColor ? UIColor.clearColor : UIColor.systemBackgroundColor;
        return;
    }
    if (_launchScreen) {
        [self buildLaunchScreen];
        return;
    }
    UIView *contentView = _contentView;
    if (_content) {
        [self addChildViewController:_content];
        contentView = _content.view;
    }
    contentView.frame = view.bounds;
    contentView.autoresizingMask =
        UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [view addSubview:contentView];
    [_content didMoveToParentViewController:self];
}

#pragma mark - The launch screen of `UILaunchScreen`

// Built as SplashBoard builds it: the named color over the whole screen, else the system background; the named image
// at its own size in the middle of the screen or of its safe area, stretched instead when the asset has slicing; a tab
// bar or a toolbar at the bottom and a navigation bar at the top, the system's own bars, or the bar's named image
// stretched over the bar's place. A name the asset catalog doesn't have leaves its part out, as iOS does.
- (void)buildLaunchScreen {
    UIView *view = self.view;
    NSString *colorName = ANNStringOrNil(_launchScreen[@"UIColorName"]);
    UIColor *color = colorName ? [UIColor colorNamed:colorName] : nil;
    view.backgroundColor = color ?: UIColor.systemBackgroundColor;

    NSString *imageName = ANNStringOrNil(_launchScreen[@"UIImageName"]);
    UIImage *image = imageName ? [UIImage imageNamed:imageName] : nil;
    if (image) {
        _imageView = [[UIImageView alloc] initWithImage:image];
        _imageView.contentMode = UIEdgeInsetsEqualToEdgeInsets(image.capInsets, UIEdgeInsetsZero)
                                     ? UIViewContentModeCenter
                                     : UIViewContentModeScaleToFill;
        id respects = _launchScreen[@"UIImageRespectsSafeAreaInsets"];
        _imageRespectsSafeArea = [respects respondsToSelector:@selector(boolValue)] &&
                                 [respects boolValue];
        [view addSubview:_imageView];
    }

    // A toolbar takes the bottom before a tab bar; a navigation bar comes on top of either.
    id toolbar = _launchScreen[@"UIToolbar"];
    id tabBar = _launchScreen[@"UITabBar"];
    if (toolbar || tabBar) {
        _bottomBar = toolbar ? [UIToolbar new] : [UITabBar new];
        _bottomBarImageView = [self addBar:_bottomBar configuration:toolbar ?: tabBar];
    }
    id navigationBar = _launchScreen[@"UINavigationBar"];
    if (navigationBar) {
        _navigationBar = [UINavigationBar new];
        _navigationBarImageView = [self addBar:_navigationBar configuration:navigationBar];
    }
}

// Adds the bar, or its image in its place when its configuration names one that loads.
- (UIImageView *)addBar:(UIView *)bar configuration:(id)configuration {
    NSString *name = ANNStringOrNil(ANNDictionaryOrNil(configuration)[@"UIImageName"]);
    UIImage *image = name ? [UIImage imageNamed:name] : nil;
    UIImageView *imageView = image ? [[UIImageView alloc] initWithImage:image] : nil;
    bar.hidden = imageView != nil;
    [self.view addSubview:bar];
    if (imageView) {
        [self.view addSubview:imageView];
    }
    return imageView;
}

// The safe area is known only in the window, so the parts are laid out here, after every change of it.
- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    if (!_launchScreen) {
        return;
    }
    CGRect bounds = self.view.bounds;
    UIEdgeInsets safeArea = self.view.safeAreaInsets;
    _imageView.frame =
        _imageRespectsSafeArea ? UIEdgeInsetsInsetRect(bounds, safeArea) : bounds;
    [self layOutBar:_bottomBar imageView:_bottomBarImageView atBottom:YES];
    [self layOutBar:_navigationBar imageView:_navigationBarImageView atBottom:NO];
}

// A bar is as tall as UIKit makes it, plus the safe area on its edge of the screen, and as wide as the screen.
- (void)layOutBar:(UIView *)bar
        imageView:(UIImageView *)imageView
         atBottom:(BOOL)atBottom {
    if (!bar) {
        return;
    }
    CGRect bounds = self.view.bounds;
    UIEdgeInsets safeArea = self.view.safeAreaInsets;
    CGFloat height = [bar sizeThatFits:bounds.size].height +
                     (atBottom ? safeArea.bottom : safeArea.top);
    CGFloat y = atBottom ? CGRectGetMaxY(bounds) - height : CGRectGetMinY(bounds);
    CGRect frame = CGRectMake(CGRectGetMinX(bounds), y, CGRectGetWidth(bounds), height);
    bar.frame = frame;
    imageView.frame = frame;
}

#pragma mark - The status bar, as the launch screen had it

- (UIStatusBarStyle)preferredStatusBarStyle {
    NSString *style = [NSBundle.mainBundle objectForInfoDictionaryKey:@"UIStatusBarStyle"];
    if ([style isEqual:@"UIStatusBarStyleLightContent"]) {
        return UIStatusBarStyleLightContent;
    }
    if ([style isEqual:@"UIStatusBarStyleDarkContent"]) {
        return UIStatusBarStyleDarkContent;
    }
    return UIStatusBarStyleDefault;
}

- (BOOL)prefersStatusBarHidden {
    id hidden = [NSBundle.mainBundle objectForInfoDictionaryKey:@"UIStatusBarHidden"];
    return [hidden respondsToSelector:@selector(boolValue)] && [hidden boolValue];
}

- (UIViewController *)childViewControllerForStatusBarStyle {
    return nil;
}

- (UIViewController *)childViewControllerForStatusBarHidden {
    return nil;
}

@end
