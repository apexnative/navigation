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

@implementation ANNSplashViewController {
    UIViewController *_content;
    UIView *_contentView;
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
