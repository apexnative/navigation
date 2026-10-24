#import "ANNTopBarPresenter.h"
#import "ANNBackButtonPresenter.h"
#import "ANNBarAppearance.h"
#import "ANNComponentPresenter.h"
#import "ANNComponentViewController.h"
#import "ANNExternalViewController.h"
#import "ANNLargeTitlePresenter.h"
#import "ANNMainList.h"
#import "ANNScreenBars.h"
#import "ANNScrollEdgeEffects.h"
#import "ANNSearchPresenter.h"
#import "ANNStackController.h"
#import "ANNTitleComponentView.h"
#import "ANNTitleGlassView.h"
#import "ANNTitleView.h"
#import "ANNToolbarPresenter.h"
#import "ANNUserChoice.h"
#import "UIViewController+LayoutProtocol.h"

// The key of the bar's handler on the screen's content mounts.
static NSString *const ANNTopBarMountKey = @"ANNTopBarPresenter";

// The offset of the main list, observed while the React background sits in it.
static void *ANNTopBarListOffsetContext = &ANNTopBarListOffsetContext;

// The row of the bar a `fill` title takes: 44 pt, 50 pt on iPad before iOS 26.
static const CGFloat ANNTitleRowHeight = 44;
static const CGFloat ANNTitleRowHeightPad = 50;

// Where the screen's appearance stands, for the bar components set onto it.
typedef NS_ENUM(NSInteger, ANNBarPhase) {
    ANNBarPhaseHidden,
    ANNBarPhaseWillAppear,
    ANNBarPhaseDidAppear,
};

static BOOL ANNIsIOS26(void) {
    if (@available(iOS 26.0, *)) {
        return YES;
    }
    return NO;
}

// The same look: the item's appearance is the one set last, or one equal to it.
static BOOL ANNSameAppearance(UINavigationBarAppearance *a, UINavigationBarAppearance *b) {
    return a == b || [a isEqual:b];
}

// The traits a look's colors and fonts resolve with: the style, the contrast, the elevation (the 1×1 background is
// drawn with them) and the text size.
static NSArray *ANNLookTraitsOf(UITraitCollection *traits) {
    if (!traits) {
        return @[];
    }
    return @[
        @(traits.userInterfaceStyle), @(traits.accessibilityContrast), @(traits.userInterfaceLevel),
        traits.preferredContentSizeCategory ?: @""
    ];
}

static BOOL ANNHasComponentName(ANNComponentOptions *component) {
    return [component.name withDefault:nil].length > 0 &&
           [component.componentId withDefault:nil].length > 0;
}

// The suggested actions of the title menu without all but Rename.
static NSArray<UIMenuElement *> *ANNRenameElementsOf(NSArray<UIMenuElement *> *elements) {
    NSMutableArray<UIMenuElement *> *kept = [NSMutableArray new];
    SEL rename = NSSelectorFromString(@"rename:");
    for (UIMenuElement *element in elements) {
        if ([element isKindOfClass:UICommand.class] && ((UICommand *)element).action == rename) {
            [kept addObject:element];
        } else if ([element isKindOfClass:UIMenu.class]) {
            NSArray<UIMenuElement *> *inner = ANNRenameElementsOf(((UIMenu *)element).children);
            [kept addObjectsFromArray:inner];
        }
    }
    return kept;
}

static NSString *const ANNRenameChoiceKey = @"topBar.title.text";

// The title the user gave: it stays over the app's `title.text` until a merge gives another text than the app had
// there (the own options', else the title the screen resolved to before the merge, as the other choices); a re-render
// that sends the old title again keeps it.
static ANNUserChoice *ANNRenameChoice(NSString *title) {
    return [ANNUserChoice choiceWithValue:title
        apply:^(ANNNavigationOptions *options, id value) {
          options.topBar.title.text = [Text withValue:value];
        }
        merged:^id(id value, ANNNavigationOptions *merge, ANNNavigationOptions *own,
                   ANNNavigationOptions *before, ANNNavigationOptions *after) {
          Text *text = merge.topBar.title.text;
          if (!text.hasValue) {
              return value;
          }
          Text *ownText = own.topBar.title.text;
          Text *was = ownText.hasValue ? ownText : before.topBar.title.text;
          NSString *merged = [text withDefault:nil];
          NSString *had = [was withDefault:nil];
          return merged == had || [merged isEqualToString:had] ? value : nil;
        }];
}

@interface ANNTopBarPresenter () <UINavigationItemRenameDelegate>
@end

@implementation ANNTopBarPresenter {
    __weak UIViewController *_screen;
    BOOL _native;
    ANNBarButtons *_buttons;
    ANNSearchPresenter *_search;
    ANNLargeTitlePresenter *_largeTitle;
    ANNScrollEdgeEffects *_edgeEffects;
    ANNMainList *_mainList;
    ANNTitleComponentView *_titleComponent;
    UIView *_titleGlass;
    // The title view the core set on the item: only that one is taken away again (a native screen's own stays).
    __weak UIView *_coreTitleView;
    ANNBarPhase _phase;
    // The bar components shown with the screen, by id: they hear its appearance moments.
    NSMutableDictionary<NSString *, UIView<ANNComponentProtocol> *> *_inBar;
    __weak UIScrollView *_observedList;
    // iOS 26, a push showing the screen: the traits its title color is resolved with until the push ends, nil
    // otherwise; every look applied meanwhile keeps it. The token tells the push that set it from a later one.
    UITraitCollection *_pinnedTraits;
    NSUInteger _pinToken;
    // A pop's band under the title keeps the bar's look as it is (the screen leaves for one without a band of its own).
    BOOL _bandKeepsLook;
    // What the last look was filled from, and the two appearances the item held right after it.
    NSArray *_lookInputs;
    UINavigationBarAppearance *_lookStandard;
    UINavigationBarAppearance *_lookEdge;
}

- (instancetype)initWithScreen:(UIViewController *)screen {
    if (self = [super init]) {
        _screen = screen;
        _native = [screen isKindOfClass:ANNExternalViewController.class];
        _registry = [[ANNReactComponentRegistry alloc] initWithCreator:screen.creator];
        _buttons = [[ANNBarButtons alloc] initWithScreen:screen registry:_registry];
        _search = [[ANNSearchPresenter alloc] initWithComponentRegistry:_registry];
        [_search bindViewController:(UIViewController<ANNLayoutProtocol> *)screen];
        _largeTitle = [[ANNLargeTitlePresenter alloc] initWithScreen:screen];
        _inBar = [NSMutableDictionary new];
    }
    return self;
}

- (void)dealloc {
    [self stopObservingList];
}

+ (ANNTopBarPresenter *)presenterOfScreen:(UIViewController *)screen {
    ANNNodePresenter *presenter = screen.presenter;
    if (![presenter isKindOfClass:ANNComponentPresenter.class]) {
        return nil;
    }
    return ((ANNComponentPresenter *)presenter).topBar;
}

#pragma mark - Places

- (nullable ANNStackController *)stack {
    UINavigationController *stack = _screen.stack;
    return [stack isKindOfClass:ANNStackController.class] ? (ANNStackController *)stack : nil;
}

// The screen is what UIKit shows on top of its stack now: the screen's appearance applies the parts the stack shares
// (also the screen a swipe reveals; never a screen that appears late under the top).
- (BOOL)isUIKitTop {
    UIViewController *screen = _screen;
    ANNStackController *stack = [self stack];
    UIViewController *entry = [stack entryOf:screen];
    return entry && stack.shownNavigationController.topViewController == entry &&
           ANNStackOptionsScreen(entry) == screen;
}

// The bar that shows the screen's stack: the stack's own, or the More navigation's while a tab of More holds the
// screens.
- (nullable UINavigationBar *)shownBar {
    return [self stack].shownNavigationController.navigationBar;
}

- (BOOL)isShownTop {
    return _phase != ANNBarPhaseHidden && [self isUIKitTop];
}

// `inline` in the row: a screen without a back button, by the options.
- (BOOL)inlineInRow:(ANNNavigationOptions *)options {
    return [ANNLargeTitleDisplayMode(options) isEqualToString:@"inline"] &&
           ![ANNBackButtonPresenter screenHasBackButton:_screen options:options];
}

- (ANNBarAppearanceContext)appearanceContextOf:(ANNNavigationOptions *)options {
    ANNBarAppearanceContext context;
    BOOL canShow = ANNLargeTitleCanShow(options);
    BOOL collapses = [ANNLargeTitlePresenter collapsesIntoList:options];
    BOOL inlineInRow = [self inlineInRow:options];
    BOOL ownEdge = [options.topBar.scrollEdgeAppearance.active withDefault:NO];
    context.transparentEdge = ANNIsIOS26() && collapses && !inlineInRow && !ownEdge;
    // While a pop draws the band behind the title in the title's own place, the bar's edge stays clear: UIKit grows
    // the bar's background with an interactive pop, over the screen's top where the title is.
    if ([_largeTitle drawsTitleBand] && !_bandKeepsLook && !ownEdge) {
        context.transparentEdge = YES;
    }
    context.largeTitleNeedsBackground = ANNIsIOS26() && canShow && !collapses && !inlineInRow;
    return context;
}

#pragma mark - The moments of the options

// A native screen's item is the app's view controller's, which it doesn't have at its creation: nothing goes there
// then.
- (void)applyOptionsOnInit:(ANNNavigationOptions *)options {
    if (_native) {
        return;
    }
    [self applyLook:options];
    [self applyTitle:options textWins:NO];
    [self applyTitleMenu:options];
    [self applyItemOptions:options];
    [_largeTitle applyMode:options shownTop:NO];
    [self applyAllButtons:options animated:NO];
    [self applyBackgroundComponent:options];
    [self updateComponentsInBar:options];
}

- (void)applyOptions:(ANNNavigationOptions *)options {
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    [self watchContentMounts];
    [self applyLook:options];
    [self applyTitle:options textWins:NO];
    [self applyTitleMenu:options];
    [ANNBackButtonPresenter applyOptions:options ofScreen:screen];
    // The back action follows the back button's visibility, also on a native screen without left buttons.
    [self updateBackAction];
    [self applyItemOptions:options];
    [self applyAllButtons:options animated:NO];
    [self applyToolbar:options animated:NO];
    [_search applyOptions:options.topBar.searchBar];
    if (!_native || options.topBar.largeTitle.hasValue) {
        [_largeTitle applyMode:options shownTop:[self isShownTop]];
    }
    [_largeTitle applyCollapse:options list:[self followedList]];
    [self applyBackgroundComponent:options];
    [[self edgeEffects] applyToAllWithOptions:options];
    [self updateListFill:options];
    [self updateComponentsInBar:options];
    // The parts the stack shares follow the screen UIKit shows on top, along the transition that shows it.
    if ([self isUIKitTop]) {
        [[self stack] applySharedBarAnimated:YES visibility:ANNSharedBarVisibilityAll];
    }
}

- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions
     resolvedOptions:(ANNNavigationOptions *)resolvedOptions {
    [self mergeOptions:mergeOptions resolvedOptions:resolvedOptions own:YES];
}

- (void)mergeParentOptions:(ANNNavigationOptions *)mergeOptions
           resolvedOptions:(ANNNavigationOptions *)resolvedOptions {
    [self mergeOptions:mergeOptions resolvedOptions:resolvedOptions own:NO];
}

- (void)mergeOptions:(ANNNavigationOptions *)mergeOptions
     resolvedOptions:(ANNNavigationOptions *)resolvedOptions
                 own:(BOOL)own {
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    ANNTopBarOptions *topBar = mergeOptions.topBar;
    ANNNavigationOptions *options = resolvedOptions;
    // What a merge writes into the screen's own options, shown or not: a merged text takes the React title out of them,
    // so it doesn't come back at the next appearance (a merged component leaves the text alone); a side color goes into
    // the own buttons.
    BOOL textWins = own && topBar.title.text.hasValue && !topBar.title.component.hasValue;
    if (textWins && [screen.options.topBar.title.component hasValue]) {
        [screen changeOwnOptions:^(ANNNavigationOptions *ownOptions) {
          ownOptions.topBar.title.component = [ANNTopBarTitleComponentOptions new];
        }];
        options = [screen resolveOptionsWithDefault];
    }
    if (own && [self writeSideColorsOf:topBar]) {
        options = [screen resolveOptionsWithDefault];
    }
    // The search takes every merge, shown or not: it waits for the appearance itself for what needs it (a focus,
    // `hideOnScroll`), and `visible: false` must close it before the screen shows again.
    if (topBar.searchBar.hasValue) {
        [_search mergeOptions:topBar.searchBar resolvedOptions:options.topBar.searchBar];
    }
    // A screen not shown only keeps the merge in its options: the bar takes it at its appearance.
    if (_phase == ANNBarPhaseHidden) {
        return;
    }
    if (topBar.hasValue) {
        [self applyLook:options];
    }
    if (topBar.title.hasValue || topBar.subtitle.hasValue || topBar.largeTitle.hasValue ||
        textWins || topBar.testID.hasValue) {
        [self applyTitle:options textWins:textWins];
    }
    ANNTopBarTitleOptions *title = topBar.title;
    if (title.menu.items != nil || title.rename.hasValue || title.document.hasValue) {
        [self applyTitleMenu:options];
    }
    if (topBar.backButton.hasValue) {
        [ANNBackButtonPresenter applyOptions:options ofScreen:screen];
        [self updateBackAction];
        // The shown back button takes a new look (its font, color and arrow) only when UIKit builds it again.
        ANNTopBarBackButtonOptions *backButton = topBar.backButton;
        if (backButton.font.hasValue || backButton.color.hasValue || backButton.icon.hasValue ||
            backButton.sfSymbol.hasValue || backButton.iconBackground.hasValue) {
            [ANNBackButtonPresenter rebuildBackButtonOfScreen:screen];
        }
    }
    [self mergeItemOptions:topBar options:options];
    [self mergeButtons:topBar options:options];
    if ([ANNToolbarPresenter mergeChangesToolbar:mergeOptions]) {
        [self applyToolbar:options animated:YES];
    }
    if (topBar.largeTitle.displayMode.hasValue || topBar.largeTitle.visible.hasValue) {
        [_largeTitle applyMode:options shownTop:[self isShownTop]];
    }
    if (topBar.largeTitle.hasValue) {
        [_largeTitle applyCollapse:options list:[self followedList]];
    }
    if (topBar.background.component.hasValue) {
        [self applyBackgroundComponent:options];
        [self updateBackgroundPlacement];
    }
    // The edge effect again whole when the merge changes it or what it depends on.
    if (mergeOptions.scrollEdgeEffect.hasValue || topBar.background.color.hasValue ||
        topBar.background.component.hasValue) {
        [[self edgeEffects] applyToAllWithOptions:options];
    }
    [self updateListFill:options];
    [self updateComponentsInBar:options];
}

- (void)applyDefaultOptionsChange:(ANNNavigationOptions *)resolvedOptions {
    if (!_screen) {
        return;
    }
    [self applyLook:resolvedOptions];
    [self applyTitle:resolvedOptions textWins:NO];
    [self applyAllButtons:resolvedOptions animated:NO];
    [self applyToolbar:resolvedOptions animated:NO];
    // The search keeps what was typed: the module sets the text of the options only once.
    [_search applyOptions:resolvedOptions.topBar.searchBar];
    [self updateListFill:resolvedOptions];
    [self updateComponentsInBar:resolvedOptions];
}

#pragma mark - The toolbar

// The screen's toolbar items. UIKit places the search bar where it goes only when the search controller is set, not
// when `toolbar.buttons` puts its `searchField` among the items or takes it out: the search places it again then, both
// ways.
- (void)applyToolbar:(ANNNavigationOptions *)options animated:(BOOL)animated {
    UIViewController *screen = _screen;
    BOOL hadSearchField = [self toolbarHasSearchField];
    [ANNToolbarPresenter applyOptions:options ofScreen:screen buttons:_buttons animated:animated];
    if (@available(iOS 26.0, *)) {
        if (hadSearchField != [self toolbarHasSearchField]) {
            [_search placeSearchBarAgain];
        }
    }
}

- (BOOL)toolbarHasSearchField {
    if (@available(iOS 26.0, *)) {
        UIViewController *screen = _screen;
        UINavigationItem *item = screen.navigationItem;
        // Without a search controller UIKit ignores the item; asking for it would only create it.
        if (!item.searchController || screen.toolbarItems.count == 0) {
            return NO;
        }
        UIBarButtonItem *field = item.searchBarPlacementBarButtonItem;
        return [screen.toolbarItems indexOfObjectIdenticalTo:field] != NSNotFound;
    }
    return NO;
}

#pragma mark - The look

// Several callers ask for the look along one appearance (the screen's, a merge, the band and the pin of iOS 26): it
// is filled only when what the fill reads changed or the item holds another look, and set only when it differs from
// the item's. A look set again makes UIKit lay the bar out anew, and on iOS 26 copy it into the stack's bar.
- (void)applyLook:(ANNNavigationOptions *)options {
    UIViewController *screen = _screen;
    UINavigationItem *item = screen.navigationItem;
    UITraitCollection *traits =
        screen.viewIfLoaded.window.traitCollection ?: screen.traitCollection;
    ANNBarAppearanceContext context = [self appearanceContextOf:options];
    UITraitCollection *pinned = ANNIsIOS26() ? _pinnedTraits : nil;
    NSArray *inputs = [self lookInputsOf:options context:context traits:traits pinned:pinned];
    if (_lookInputs && ANNOptionValuesSame(_lookInputs, inputs) &&
        ANNSameAppearance(item.standardAppearance, _lookStandard) &&
        ANNSameAppearance(item.scrollEdgeAppearance, _lookEdge)) {
        return;
    }
    // The screen's own looks, filled whole and set again (the item copies them).
    UINavigationBarAppearance *standard =
        [item.standardAppearance copy] ?: [UINavigationBarAppearance new];
    UINavigationBarAppearance *edge =
        [item.scrollEdgeAppearance copy] ?: [UINavigationBarAppearance new];
    [ANNBarAppearance fillStandard:standard
                        scrollEdge:edge
                       withOptions:options
                           context:context
                            traits:traits];
    if (pinned) {
        [self pinTitleColorsOf:standard edge:edge options:options traits:pinned];
    }
    if (!ANNSameAppearance(item.standardAppearance, standard)) {
        item.standardAppearance = standard;
    }
    if (!ANNSameAppearance(item.scrollEdgeAppearance, edge)) {
        item.scrollEdgeAppearance = edge;
    }
    _lookInputs = inputs;
    _lookStandard = item.standardAppearance;
    _lookEdge = item.scrollEdgeAppearance;
}

// What `fillStandard:scrollEdge:withOptions:context:traits:` and the pin read: the bar's options by value, the
// image the back button's icon loads (a file written again at its path is another image), the context, the traits.
- (NSArray *)lookInputsOf:(ANNNavigationOptions *)options
                  context:(ANNBarAppearanceContext)context
                   traits:(UITraitCollection *)traits
                   pinned:(nullable UITraitCollection *)pinned {
    ANNTopBarOptions *topBar = options.topBar;
    Image *backIcon = topBar.backButton.icon;
    ANNImageSource *source = backIcon.source;
    BOOL loads = backIcon.hasValue && !(source && source.kind == ANNImageSourceKindSymbol);
    id backImage = loads ? [backIcon withDefault:nil] : nil;
    return @[
        topBar.background, topBar.scrollEdgeAppearance, topBar.noBorder, topBar.borderColor,
        topBar.title.font, topBar.title.color, topBar.largeTitle.font, topBar.largeTitle.color,
        topBar.backButton, backImage ?: NSNull.null, @(context.transparentEdge),
        @(context.largeTitleNeedsBackground), ANNLookTraitsOf(traits),
        pinned ? ANNLookTraitsOf(pinned) : NSNull.null
    ];
}

// The title colors resolved for `traits`: fixed colors that don't follow the glass's style.
- (void)pinTitleColorsOf:(UINavigationBarAppearance *)standard
                    edge:(UINavigationBarAppearance *)edge
                 options:(ANNNavigationOptions *)options
                  traits:(UITraitCollection *)traits {
    ANNTopBarOptions *topBar = options.topBar;
    NSMutableDictionary<NSAttributedStringKey, id> *title =
        [standard.titleTextAttributes mutableCopy];
    title[NSForegroundColorAttributeName] =
        [ANNBarAppearance pinnedTitleColor:topBar.title.color traits:traits];
    standard.titleTextAttributes = title;
    ANNTopBarScrollEdgeAppearanceTitleOptions *edgeOptions = topBar.scrollEdgeAppearance.title;
    Color *edgeColor = edgeOptions.hasValue ? edgeOptions.color : topBar.title.color;
    NSMutableDictionary<NSAttributedStringKey, id> *edgeTitle =
        [edge.titleTextAttributes mutableCopy];
    edgeTitle[NSForegroundColorAttributeName] =
        [ANNBarAppearance pinnedTitleColor:edgeColor traits:traits];
    edge.titleTextAttributes = edgeTitle;
}

// The pin holds through every look applied during the push (a merge, a change of the defaults) and goes at its end,
// unless a later push pinned again.
- (void)pinTitleColorAlong:(id<UIViewControllerTransitionCoordinator>)coordinator
                    traits:(UITraitCollection *)traits {
    UIViewController *screen = _screen;
    if (!screen || !ANNIsIOS26()) {
        return;
    }
    NSUInteger token = ++_pinToken;
    _pinnedTraits = traits;
    [self applyLook:[screen resolveOptionsWithDefault]];
    __weak ANNTopBarPresenter *weakSelf = self;
    void (^restore)(id<UIViewControllerTransitionCoordinatorContext>) =
        ^(id<UIViewControllerTransitionCoordinatorContext> context) {
          [weakSelf endTitleColorPin:token];
        };
    BOOL along = [coordinator animateAlongsideTransition:nil completion:restore];
    if (!along) {
        [self endTitleColorPin:token];
    }
}

- (void)endTitleColorPin:(NSUInteger)token {
    if (token != _pinToken || !_pinnedTraits) {
        return;
    }
    _pinnedTraits = nil;
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    [self applyLook:[screen resolveOptionsWithDefault]];
    // A stack merge during the push copied the pinned look into the bar's own copy (iOS 26): it takes the item's again.
    if ([self isUIKitTop]) {
        [[self stack] applySharedBarAnimated:NO visibility:ANNSharedBarVisibilityNone];
    }
}

#pragma mark - The title area

- (void)applyTitle:(ANNNavigationOptions *)options textWins:(BOOL)textWins {
    UIViewController *screen = _screen;
    UINavigationItem *item = screen.navigationItem;
    ANNTopBarOptions *topBar = options.topBar;
    ANNTopBarTitleOptions *title = topBar.title;
    if (!_native || title.text.hasValue) {
        item.title = [title.text withDefault:nil];
    }
    NSString *subtitle = [topBar.subtitle.text withDefault:nil];
    NSString *glass = [title.glass withDefault:nil];
    BOOL glassShows = NO;
    if (@available(iOS 26.0, *)) {
        glassShows = [ANNTitleGlassView isGlass:glass];
    }
    [self applySubtitle:options glass:glassShows];

    // A React title (without a name there is none, and the current one goes; the text stays).
    UIView<ANNComponentProtocol> *root = nil;
    if (!textWins && ![self ownTextHidesComponent] && ANNHasComponentName(title.component)) {
        root = [_registry componentView:title.component type:ANNComponentTypeTopBarTitle];
    }
    if (root && _titleComponent.content != root) {
        _titleComponent = [[ANNTitleComponentView alloc] initWithContent:root];
        __weak ANNTopBarPresenter *weakSelf = self;
        _titleComponent.sizeDidChange = ^{
          [weakSelf titleSizeDidChange];
        };
    } else if (!root) {
        _titleComponent = nil;
    }
    NSString *alignment = [title.component.alignment withDefault:@"center"];
    _titleComponent.fills = [alignment isEqualToString:@"fill"];
    // A `fill` title takes its row before the capsule and the item take it, so both see its final size.
    BOOL fillMoved = [self placeFillTitle];

    UIView *titleView = nil;
    if (glassShows) {
        if (@available(iOS 26.0, *)) {
            if (_titleComponent) {
                titleView = [[ANNTitleGlassView alloc] initWithGlass:glass
                                                           component:_titleComponent
                                                             options:options];
            } else {
                titleView = [[ANNTitleGlassView alloc] initWithGlass:glass options:options];
            }
        }
    } else if (_titleComponent) {
        titleView = _titleComponent;
    } else if (!ANNIsIOS26() && subtitle.length > 0 && !ANNLargeTitleCanShow(options)) {
        // Before iOS 26 a subtitle is a two-label title, without a large title that can show.
        titleView = [[ANNTitleView alloc] initWithOptions:options];
    }
    _titleGlass = glassShows ? titleView : nil;
    // A title view the app's own item has (a native screen) stays while the core shows none; one the core set goes
    // (a stopped React title, a capsule turned off).
    UIView *current = item.titleView;
    if (!titleView && current && current != _coreTitleView) {
        return;
    }
    if (current != titleView) {
        item.titleView = titleView;
    }
    _coreTitleView = titleView;
    // The view the item already had changed its row: UIKit measures it only as it is set.
    if (fillMoved && current == titleView) {
        [self titleSizeDidChange];
    }
}

// The layers of the title: the screen's own text hides a component of its stack or of the defaults; a component in the
// screen's own options shows over its own text.
- (BOOL)ownTextHidesComponent {
    UIViewController *screen = _screen;
    ANNTopBarTitleOptions *own = screen.options.topBar.title;
    BOOL ownText = (own.text.hasValue && !own.text.isNull) ||
                   [screen userChoiceForKey:ANNRenameChoiceKey] != nil;
    return ownText && !ANNHasComponentName(own.component);
}

// iOS 26: the system subtitle under the title and under the large title, with only the attributes given (a color, or
// a whole font of `fontSize` or the place's size, 12 / 11 pt); on the glass the small one is a line of the capsule.
- (void)applySubtitle:(ANNNavigationOptions *)options glass:(BOOL)glass {
    if (@available(iOS 26.0, *)) {
        UINavigationItem *item = _screen.navigationItem;
        ANNTopBarSubtitleOptions *subtitle = options.topBar.subtitle;
        NSString *text = [subtitle.text withDefault:nil];
        if (_native && !subtitle.text.hasValue) {
            return;
        }
        if (text.length == 0) {
            item.attributedSubtitle = nil;
            item.largeAttributedSubtitle = nil;
            return;
        }
        item.attributedSubtitle =
            glass ? nil : [self subtitle:text options:subtitle placeSize:12];
        item.largeAttributedSubtitle = [self subtitle:text options:subtitle placeSize:11];
    }
}

- (NSAttributedString *)subtitle:(NSString *)text
                         options:(ANNTopBarSubtitleOptions *)subtitle
                       placeSize:(CGFloat)placeSize {
    NSMutableDictionary<NSAttributedStringKey, id> *attributes = [NSMutableDictionary new];
    UIFont *font = [subtitle.font fontWithPlaceFont:[UIFont systemFontOfSize:placeSize]];
    if (font) {
        attributes[NSFontAttributeName] = font;
    }
    Color *color = subtitle.color;
    if (color.hasValue && !color.isNoColor) {
        attributes[NSForegroundColorAttributeName] =
            [color withDefault:UIColor.secondaryLabelColor];
    }
    return [[NSAttributedString alloc] initWithString:text attributes:attributes];
}

// A centred React title changed its size: UIKit measures the view only as it is set.
- (void)titleSizeDidChange {
    UINavigationItem *item = _screen.navigationItem;
    UIView *titleView = item.titleView;
    if (@available(iOS 26.0, *)) {
        if ([titleView isKindOfClass:ANNTitleGlassView.class]) {
            [(ANNTitleGlassView *)titleView componentSizeDidChange];
        }
    }
    if (titleView == _titleComponent || titleView == _titleGlass) {
        item.titleView = nil;
        item.titleView = titleView;
    }
}

// A `fill` React title takes the row of the bar, once the screen is in its stack: the bar's width, 44 pt on iOS 26;
// before it 44 pt (50 pt on iPad), never taller than the bar (the compact landscape bar is shorter). YES when the row
// changed. The row placed last is the measure, not the frame: UIKit narrows that between the buttons.
- (BOOL)placeFillTitle {
    ANNTitleComponentView *title = _titleComponent;
    UINavigationBar *bar = [self shownBar];
    if (!title.fills || !bar) {
        return NO;
    }
    CGFloat height = ANNTitleRowHeight;
    if (!ANNIsIOS26()) {
        if (_screen.traitCollection.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            height = ANNTitleRowHeightPad;
        }
        if (bar.bounds.size.height > 0) {
            height = MIN(height, bar.bounds.size.height);
        }
    }
    CGSize row = CGSizeMake(bar.bounds.size.width, height);
    if (CGSizeEqualToSize(title.rowSize, row)) {
        return NO;
    }
    title.rowSize = row;
    title.frame = CGRectMake(0, 0, row.width, row.height);
    [title invalidateIntrinsicContentSize];
    return YES;
}

// At an appearance and at a layout of the screen the row may have changed (the screen is in its stack now, a
// rotation): the item then gets its title view again, the capsule resized around it.
- (void)layOutFillTitle {
    if ([self placeFillTitle]) {
        [self titleSizeDidChange];
    }
}

// The title menu (iOS 16+): built from the screen's resolved options at each open — Rename first, then the items;
// `items: []` removes it unless `rename` or `document` is set.
- (void)applyTitleMenu:(ANNNavigationOptions *)options {
    if (@available(iOS 16.0, *)) {
        UINavigationItem *item = _screen.navigationItem;
        ANNTopBarTitleOptions *title = options.topBar.title;
        if (_native && !title.menu.hasValue && !title.rename.hasValue && !title.document.hasValue) {
            return;
        }
        BOOL rename = [title.rename withDefault:NO];
        NSURL *documentURL =
            [ANNTopBarPresenter documentURLOf:[title.document.url withDefault:nil]];
        item.renameDelegate = rename ? self : nil;
        item.documentProperties =
            documentURL ? [[UIDocumentProperties alloc] initWithURL:documentURL] : nil;
        if (title.menu.items.count == 0 && !rename && !documentURL) {
            item.titleMenuProvider = nil;
            return;
        }
        __weak ANNTopBarPresenter *weakSelf = self;
        item.titleMenuProvider = ^UIMenu *(NSArray<UIMenuElement *> *suggestedActions) {
          return [weakSelf titleMenuWithSuggestedActions:suggestedActions];
        };
    }
}

+ (nullable NSURL *)documentURLOf:(nullable NSString *)url {
    if (url.length == 0) {
        return nil;
    }
    NSURL *parsed = [NSURL URLWithString:url];
    if (parsed.scheme.length > 0) {
        return parsed;
    }
    return [NSURL fileURLWithPath:[url stringByExpandingTildeInPath]];
}

- (UIMenu *)titleMenuWithSuggestedActions:(NSArray<UIMenuElement *> *)suggestedActions
    API_AVAILABLE(ios(16.0)) {
    ANNNavigationOptions *options = [_screen resolveOptionsWithDefault];
    ANNTopBarTitleOptions *title = options.topBar.title;
    NSMutableArray<UIMenuElement *> *children = [NSMutableArray new];
    if ([title.rename withDefault:NO]) {
        NSArray<UIMenuElement *> *rename = ANNRenameElementsOf(suggestedActions);
        if (rename.count > 0) {
            [children addObject:[UIMenu menuWithTitle:@""
                                                image:nil
                                           identifier:nil
                                              options:UIMenuOptionsDisplayInline
                                             children:rename]];
        }
    }
    NSString *menuTitle = @"";
    if (title.menu.items.count > 0) {
        __weak ANNBarButtons *buttons = _buttons;
        UIMenu *menu = [title.menu menuWithPickHandler:^(NSString *itemId) {
          [buttons menuItemPicked:itemId
              choiceKey:@"topBar.title.menu"
              menuOf:^ANNMenuOptions *(ANNNavigationOptions *options) {
                return options.topBar.title.menu;
              }
              setMenu:^(ANNNavigationOptions *options, ANNMenuOptions *copy) {
                options.topBar.title.menu = copy;
              }];
        }];
        menuTitle = menu.title;
        [children addObjectsFromArray:menu.children];
    }
    return [UIMenu menuWithTitle:menuTitle children:children];
}

#pragma mark - UINavigationItemRenameDelegate

// Empty or blank: the old title back, no event; the same: nothing; new: `title.text` in the screen's user layer, the
// title drawn again, then `ANN.TitleRenamed`.
- (void)navigationItem:(UINavigationItem *)navigationItem
    didEndRenamingWithTitle:(NSString *)title {
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    NSString *old = [[screen resolveOptionsWithDefault].topBar.title.text withDefault:nil] ?: @"";
    NSString *trimmed =
        [title stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
    if (trimmed.length == 0) {
        navigationItem.title = old;
        return;
    }
    if ([title isEqualToString:old]) {
        return;
    }
    [screen setUserChoice:ANNRenameChoice(title) forKey:ANNRenameChoiceKey];
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    [self applyTitle:options textWins:NO];
    NSString *componentId = screen.layoutInfo.componentId;
    if (componentId.length > 0) {
        [screen.eventEmitter sendTitleRenamed:componentId text:title];
    }
}

#pragma mark - The item's other options

- (void)applyItemOptions:(ANNNavigationOptions *)options {
    UINavigationItem *item = _screen.navigationItem;
    ANNTopBarOptions *topBar = options.topBar;
    if (@available(iOS 16.0, *)) {
        // Without the option UIKit's style is left.
        NSString *style = [topBar.style withDefault:nil];
        if ([style isEqualToString:@"browser"]) {
            item.style = UINavigationItemStyleBrowser;
        } else if ([style isEqualToString:@"editor"]) {
            item.style = UINavigationItemStyleEditor;
        } else if ([style isEqualToString:@"navigator"]) {
            item.style = UINavigationItemStyleNavigator;
        }
    }
    [self applyMinimization:topBar];
    [self applyVerticalBarKeeps:topBar];
}

- (void)mergeItemOptions:(ANNTopBarOptions *)merge options:(ANNNavigationOptions *)options {
    if (merge.style.hasValue) {
        [self applyItemOptions:options];
        return;
    }
    if (merge.hideOnScroll.hasValue) {
        [self applyMinimization:options.topBar];
    }
    if (merge.verticalBarKeeps.hasValue) {
        [self applyVerticalBarKeeps:options.topBar];
    }
}

// iOS 27: the bar minimizes as the content scrolls down only with `hideOnScroll`; otherwise never by itself.
- (void)applyMinimization:(ANNTopBarOptions *)topBar {
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270000
    if (@available(iOS 27.0, *)) {
        UINavigationItem *item = _screen.navigationItem;
        UIBarMinimization *minimization =
            [item.navigationBarMinimization copy] ?: [UIBarMinimization new];
        minimization.minimizationBehavior = [topBar.hideOnScroll withDefault:NO]
                                                ? UIBarMinimizationBehaviorOnScrollDown
                                                : UIBarMinimizationBehaviorNever;
        item.navigationBarMinimization = minimization;
    }
#endif
}

// iOS 27.1: which of the tabs and the bar's buttons keep their place in the vertical bar.
- (void)applyVerticalBarKeeps:(ANNTopBarOptions *)topBar {
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
    if (@available(iOS 27.1, *)) {
        NSString *keeps = [topBar.verticalBarKeeps withDefault:nil];
        UIVerticalBarCompressionBehavior behavior = UIVerticalBarCompressionBehaviorAutomatic;
        if ([keeps isEqualToString:@"tabs"]) {
            behavior = UIVerticalBarCompressionBehaviorPrefersTabBar;
        } else if ([keeps isEqualToString:@"buttons"]) {
            behavior = UIVerticalBarCompressionBehaviorPrefersBarItems;
        }
        _screen.navigationItem.verticalBarCompressionBehavior = behavior;
    }
#endif
}

#pragma mark - Buttons

- (void)applyAllButtons:(ANNNavigationOptions *)options animated:(BOOL)animated {
    ANNTopBarOptions *topBar = options.topBar;
    // A native screen keeps the buttons its app set where the options have none.
    if (!_native || topBar.leftButtons) {
        [_buttons applySide:ANNBarButtonsSideLeft options:options animated:animated];
        [self updateBackAction];
    }
    if (!_native || topBar.rightButtons) {
        [_buttons applySide:ANNBarButtonsSideRight options:options animated:animated];
    }
    if (!_native || topBar.centerButtons) {
        [_buttons applySide:ANNBarButtonsSideCenter options:options animated:NO];
    }
    if (!_native || topBar.overflowMenu.hasValue) {
        [_buttons applyOverflowMenu:options];
    }
}

// From iOS 26 UIKit animates a change of the bar's items by itself, also with `animated:NO` (no public switch turns it
// off): `animate…Buttons: false` changes them without the core's own animation only.
- (void)mergeButtons:(ANNTopBarOptions *)merge options:(ANNNavigationOptions *)options {
    ANNTopBarOptions *topBar = options.topBar;
    if (merge.leftButtons || merge.leftButtonColor.hasValue ||
        merge.leftButtonDisabledColor.hasValue || merge.leftButtonBackgroundColor.hasValue) {
        [_buttons applySide:ANNBarButtonsSideLeft
                    options:options
                   animated:[topBar.animateLeftButtons withDefault:NO]];
        [self updateBackAction];
    }
    if (merge.rightButtons || merge.rightButtonColor.hasValue ||
        merge.rightButtonDisabledColor.hasValue || merge.rightButtonBackgroundColor.hasValue) {
        [_buttons applySide:ANNBarButtonsSideRight
                    options:options
                   animated:[topBar.animateRightButtons withDefault:NO]];
    }
    if (merge.centerButtons || merge.centerButtonColor.hasValue ||
        merge.centerButtonDisabledColor.hasValue || merge.customizationId.hasValue) {
        [_buttons applySide:ANNBarButtonsSideCenter options:options animated:NO];
    }
    if (merge.overflowMenu.hasValue || merge.overflowMenu.items) {
        [_buttons applyOverflowMenu:options];
    }
}

// A side color from a merge goes into the screen's own buttons, their own colors included, so it survives a new
// appearance and a theme change; a side background color into the own image buttons. Buttons from the defaults or the
// parents never get it. YES when the own options changed.
- (BOOL)writeSideColorsOf:(ANNTopBarOptions *)merge {
    UIViewController *screen = _screen;
    ANNTopBarOptions *own = screen.options.topBar;
    BOOL left = (merge.leftButtonColor.hasValue || merge.leftButtonBackgroundColor.hasValue) &&
                own.leftButtons;
    BOOL right = (merge.rightButtonColor.hasValue || merge.rightButtonBackgroundColor.hasValue) &&
                 own.rightButtons;
    BOOL center = merge.centerButtonColor.hasValue && own.centerButtons;
    if (!left && !right && !center) {
        return NO;
    }
    [screen changeOwnOptions:^(ANNNavigationOptions *options) {
      ANNTopBarOptions *topBar = options.topBar;
      if (left) {
          topBar.leftButtons = [ANNTopBarPresenter buttons:topBar.leftButtons
                                                 withColor:merge.leftButtonColor
                                                background:merge.leftButtonBackgroundColor];
      }
      if (right) {
          topBar.rightButtons = [ANNTopBarPresenter buttons:topBar.rightButtons
                                                  withColor:merge.rightButtonColor
                                                 background:merge.rightButtonBackgroundColor];
      }
      if (center) {
          topBar.centerButtons = [ANNTopBarPresenter buttons:topBar.centerButtons
                                                   withColor:merge.centerButtonColor
                                                  background:nil];
      }
    }];
    return YES;
}

+ (NSArray<ANNButtonOptions *> *)buttons:(NSArray<ANNButtonOptions *> *)buttons
                               withColor:(Color *)color
                              background:(nullable Color *)background {
    NSMutableArray<ANNButtonOptions *> *changed = [NSMutableArray arrayWithCapacity:buttons.count];
    for (ANNButtonOptions *button in buttons) {
        ANNButtonOptions *copy = [button copy];
        if (button.menu.items.count > 0) {
            copy.menu = [ANNBarButtons deepCopyOfMenu:button.menu];
        }
        if (color.hasValue) {
            copy.color = color;
        }
        // Only image buttons (`icon`, not SF Symbols) take a side's background.
        BOOL imageIcon =
            [button.icon withDefault:nil] && [button.sfSymbol withDefault:nil].length == 0;
        if (background.hasValue && imageIcon) {
            copy.iconBackground.color = background;
        }
        [changed addObject:copy];
    }
    return changed;
}

// Left buttons replace the back button, `backButton.visible: false` hides it: the stack's back action, which would make
// UIKit show one anyway, goes then.
- (void)updateBackAction {
    UIViewController *screen = _screen;
    ANNStackController *stack = [self stack];
    UIViewController *entry = [stack entryOf:screen];
    if (entry && ANNStackOptionsScreen(entry) == screen) {
        [stack updateBackActionOf:entry];
    }
}

#pragma mark - The React components of the bar

- (void)applyBackgroundComponent:(ANNNavigationOptions *)options {
    ANNTopBarBackgroundComponentOptions *component = options.topBar.background.component;
    UIView<ANNComponentProtocol> *root = nil;
    if (ANNHasComponentName(component)) {
        root = [_registry componentView:component type:ANNComponentTypeTopBarBackground];
    }
    if (!root) {
        _background = nil;
        return;
    }
    if (_background.content != root) {
        _background = [[ANNBarBackgroundComponent alloc] initWithContent:root];
    }
}

- (void)collectWaitedComponents:(void (^)(NSString *componentId, BOOL isButton))collect {
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    ANNTopBarOptions *topBar = options.topBar;
    ANNTopBarTitleComponentOptions *title = topBar.title.component;
    if (ANNHasComponentName(title) && [title.waitForRender withDefault:YES] &&
        ![self ownTextHidesComponent]) {
        UIView<ANNComponentProtocol> *root =
            [_registry componentView:title type:ANNComponentTypeTopBarTitle];
        if (root && ![_registry isComponentReady:root.componentId]) {
            collect(root.componentId, NO);
        }
    }
    ANNTopBarBackgroundComponentOptions *background = topBar.background.component;
    if (ANNHasComponentName(background) && [background.waitForRender withDefault:YES]) {
        UIView<ANNComponentProtocol> *root =
            [_registry componentView:background type:ANNComponentTypeTopBarBackground];
        if (root && ![_registry isComponentReady:root.componentId]) {
            collect(root.componentId, NO);
        }
    }
    for (UIView<ANNComponentProtocol> *root in [_buttons componentButtonsOf:options]) {
        if (![_registry isComponentReady:root.componentId]) {
            collect(root.componentId, YES);
        }
    }
}

// What the bar shows of the screen's components now: those that came hear the screen's moment as it stands, those that
// went hear `DidDisappear` and stop at once.
- (void)updateComponentsInBar:(ANNNavigationOptions *)options {
    NSMutableDictionary<NSString *, UIView<ANNComponentProtocol> *> *current =
        [NSMutableDictionary new];
    UIView<ANNComponentProtocol> *title = _titleComponent.content;
    if (title) {
        current[title.componentId] = title;
    }
    UIView<ANNComponentProtocol> *background = _background.content;
    if (background) {
        current[background.componentId] = background;
    }
    NSMutableSet<NSString *> *buttonIds = [NSMutableSet new];
    for (UIView<ANNComponentProtocol> *button in [_buttons componentButtonsOf:options]) {
        current[button.componentId] = button;
        [buttonIds addObject:button.componentId];
    }
    [_buttons keepSlotsOf:buttonIds];
    for (NSString *componentId in _inBar.allKeys) {
        UIView<ANNComponentProtocol> *shown = _inBar[componentId];
        if (current[componentId] != shown) {
            [shown componentDidDisappear];
        }
    }
    // Roots of the bar nobody names any more stop now (the search's results are the module's).
    for (UIView<ANNComponentProtocol> *root in _registry.allComponents) {
        if (root.componentType != ANNComponentTypeComponent && current[root.componentId] != root) {
            [_registry removeChildComponent:root.componentId];
        }
    }
    for (NSString *componentId in current) {
        UIView<ANNComponentProtocol> *component = current[componentId];
        if (_inBar[componentId] == component) {
            continue;
        }
        if (_phase != ANNBarPhaseHidden) {
            [component componentWillAppear];
        }
        if (_phase == ANNBarPhaseDidAppear) {
            [component componentDidAppear];
        }
    }
    _inBar = current;
}

#pragma mark - The screen's moments

- (void)screenWillAppear {
    _phase = ANNBarPhaseWillAppear;
    for (UIView<ANNComponentProtocol> *component in _inBar.allValues) {
        [component componentWillAppear];
    }
}

- (void)screenDidAppear {
    _phase = ANNBarPhaseDidAppear;
    for (UIView<ANNComponentProtocol> *component in _inBar.allValues) {
        [component componentDidAppear];
    }
    [_search componentDidAppear];
    UIViewController *screen = _screen;
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    BOOL band = [_largeTitle drawsTitleBand];
    [_largeTitle screenDidAppear:options list:[self followedList]];
    [_largeTitle removeTitlePlaceFill];
    // The bar's own look again, now that the title is in the bar.
    if (band) {
        [self applyLook:options];
    }
    [self layOutFillTitle];
    [self updateBackgroundPlacement];
}

- (void)screenDidDisappear {
    _phase = ANNBarPhaseHidden;
    for (UIView<ANNComponentProtocol> *component in _inBar.allValues) {
        [component componentDidDisappear];
    }
    [_search componentDidDisappear];
    UIViewController *screen = _screen;
    BOOL band = [_largeTitle drawsTitleBand];
    if (screen) {
        ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
        [_largeTitle screenDidDisappear:options list:[self followedList]];
    }
    // The lists the screen set leave with it; its color and its React background leave the main list.
    [self releaseLists];
    _bandKeepsLook = NO;
    // A swipe back given up: the screen keeps the bar's own look for its next appearance.
    if (band && screen) {
        [self applyLook:[screen resolveOptionsWithDefault]];
    }
}

- (void)releaseLists {
    [_edgeEffects reset];
    [_largeTitle removeListFill];
    [_largeTitle removeTitlePlaceFill];
    [_largeTitle removeTitleHost];
    [self stopObservingList];
    [_background returnFromListToBar:[self shownBar]];
}

- (void)stopComponents {
    [self stopObservingList];
    [_registry stopAll];
    [_inBar removeAllObjects];
    _titleComponent = nil;
    _background = nil;
}

#pragma mark - Lists

- (nullable ANNScrollEdgeEffects *)edgeEffects {
    UIView *view = _screen.viewIfLoaded;
    if (!_edgeEffects && view) {
        _edgeEffects = [[ANNScrollEdgeEffects alloc] initWithScreenView:view];
    }
    return _edgeEffects;
}

- (nullable ANNMainList *)mainList {
    UIView *view = _screen.viewIfLoaded;
    if (!_mainList && view) {
        _mainList = [[ANNMainList alloc] initWithScreenView:view];
    }
    return _mainList;
}

// The screen's content tells each of its mounts (the work only for a screen in a window).
- (void)watchContentMounts {
    UIView<ANNComponentProtocol> *content = _screen.reactView;
    if (!content) {
        return;
    }
    __weak ANNTopBarPresenter *weakSelf = self;
    [content setContentDidMount:^{
      [weakSelf contentDidMount];
    }
                         forKey:ANNTopBarMountKey];
}

// The list the bar follows at the top edge under a large title that folds into it: the main list of a React screen;
// a native screen's views are the app's, the bar chooses there by itself.
- (nullable UIScrollView *)followedList {
    return _native ? nil : [[self mainList] list];
}

- (void)contentDidMount {
    UIViewController *screen = _screen;
    if (!screen.viewIfLoaded.window || _phase == ANNBarPhaseHidden) {
        // Lists React took out of the screen get the system edge effect back, in a window or not.
        [_edgeEffects applyToList:nil options:[screen resolveOptionsWithDefault]];
        return;
    }
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    [[self edgeEffects] applyToList:[[self mainList] list] options:options];
    [_largeTitle applyCollapse:options list:[self followedList]];
    [self updateListFill:options];
    [self updateBackgroundPlacement];
}

- (void)screenDidLayOut {
    UIViewController *screen = _screen;
    if (!screen.viewIfLoaded.window || _phase == ANNBarPhaseHidden) {
        return;
    }
    if (_phase == ANNBarPhaseDidAppear) {
        [_largeTitle noteTopAtRest];
    }
    [self updateListFill:[screen resolveOptionsWithDefault]];
    [self layOutFillTitle];
    [self updateBackgroundPlacement];
}

- (void)shownFromLowerBarWithBottom:(CGFloat)oldBarBottom {
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    [_largeTitle fillTitlePlaceFrom:oldBarBottom
                               list:[[self mainList] list]
                            options:[screen resolveOptionsWithDefault]];
}

// The top of the safe area the screen takes on the stack is that of its place while it waited parked: UIKit lays it out
// only inside the push.
- (void)arrivesByPushOn:(UINavigationController *)navigation {
    UIViewController *screen = _screen;
    if (_native || ![screen isKindOfClass:ANNComponentViewController.class]) {
        return;
    }
    ANNComponentViewController *component = (ANNComponentViewController *)screen;
    ANNScreenPlace * (^placeWhileParked)(void) = component.placeWhileParked;
    ANNScreenPlace *place = placeWhileParked ? placeWhileParked() : nil;
    if (!place || !component.reactView.isReady) {
        return;
    }
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    [_largeTitle screenInPush:options
                         list:[self followedList]
                  inlineInRow:[self inlineInRow:options]
                          top:place.safeArea.top
                     stackTop:navigation.view.safeAreaInsets.top];
}

// A native screen's views are the app's: the core lays nothing into them. The band behind the title is filled where
// the title is, and the look follows it, before UIKit lays the screen out for the transition.
- (BOOL)revealedByPop {
    UIViewController *screen = _screen;
    if (!screen || _native) {
        return NO;
    }
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    [_largeTitle screenInPop:options list:[self followedList] inlineInRow:[self inlineInRow:options]];
    return _largeTitle.hostsTitle;
}

- (void)revealedByPopFrom:(ANNPopBar)bar {
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    [_largeTitle fillTitlePlaceOfPopFrom:bar list:[[self mainList] list] options:options];
    if ([_largeTitle drawsTitleBand]) {
        [self applyLook:options];
    }
}

- (void)leavesByPopClearingEdge:(BOOL)clearsEdge {
    UIViewController *screen = _screen;
    if (!screen || _native) {
        return;
    }
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    [_largeTitle screenInPop:options list:[self followedList] inlineInRow:[self inlineInRow:options]];
    if (_largeTitle.hostsTitle) {
        [_largeTitle fillTitlePlaceFrom:0 list:[[self mainList] list] options:options];
    }
    _bandKeepsLook = !clearsEdge;
    if (clearsEdge && [_largeTitle drawsTitleBand]) {
        [self applyLook:options];
    }
}

- (void)popGivenUp {
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    BOOL band = [_largeTitle drawsTitleBand];
    [_largeTitle popEnded:options list:[self followedList]];
    [_largeTitle removeTitlePlaceFill];
    _bandKeepsLook = NO;
    if (band) {
        [self applyLook:options];
    }
}

- (BOOL)clearsBarAtEdge {
    UIViewController *screen = _screen;
    return screen && [self appearanceContextOf:[screen resolveOptionsWithDefault]].transparentEdge;
}

#pragma mark - The React background in the list (iOS 26)

// With a large title that folds into the list, the shown top screen's React background is a fill at the bottom of the
// main list while its rows are below the bar — from the screen's top (the bar's top with `clipToBounds`) to the bar's
// bottom, to the first row when pulled — and in the bar's background otherwise; again at every move of the list.
- (void)updateBackgroundPlacement {
    ANNBarBackgroundComponent *background = _background;
    UIViewController *screen = _screen;
    UINavigationBar *bar = [self shownBar];
    if (!background || !screen || !bar) {
        [self stopObservingList];
        return;
    }
    ANNNavigationOptions *options = [screen resolveOptionsWithDefault];
    BOOL eligible = ANNIsIOS26() && [self isShownTop] && screen.viewIfLoaded.window != nil &&
                    [background isShownInBar:bar] &&
                    [ANNLargeTitlePresenter collapsesIntoList:options] &&
                    ![self inlineInRow:options];
    UIScrollView *list = eligible ? [[self mainList] list] : nil;
    if (!list) {
        [self stopObservingList];
        [background returnFromListToBar:bar];
        return;
    }
    [self observeList:list];
    CGFloat barTop = [bar convertPoint:CGPointZero toView:list].y;
    CGFloat barBottom = [bar convertPoint:CGPointMake(0, CGRectGetMaxY(bar.bounds)) toView:list].y;
    // The first row is the content's origin.
    if (barBottom > 0.5) {
        [background returnFromListToBar:bar];
        return;
    }
    CGFloat top = [options.topBar.background.clipToBounds withDefault:NO]
                      ? barTop
                      : [screen.view convertPoint:CGPointZero toView:list].y;
    CGFloat bottom = MAX(barBottom, 0);
    if (bottom <= top) {
        [background returnFromListToBar:bar];
        return;
    }
    [background placeInList:list frame:CGRectMake(0, top, list.bounds.size.width, bottom - top)];
}

- (void)observeList:(UIScrollView *)list {
    if (_observedList == list) {
        return;
    }
    [self stopObservingList];
    [list addObserver:self
           forKeyPath:@"contentOffset"
              options:0
              context:ANNTopBarListOffsetContext];
    _observedList = list;
}

- (void)stopObservingList {
    UIScrollView *list = _observedList;
    if (list) {
        [list removeObserver:self forKeyPath:@"contentOffset" context:ANNTopBarListOffsetContext];
    }
    _observedList = nil;
}

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary<NSKeyValueChangeKey, id> *)change
                       context:(void *)context {
    if (context != ANNTopBarListOffsetContext) {
        [super observeValueForKeyPath:keyPath ofObject:object change:change context:context];
        return;
    }
    [self updateBackgroundPlacement];
}

- (void)updateListFill:(ANNNavigationOptions *)options {
    UIViewController *screen = _screen;
    if (!screen.viewIfLoaded.window) {
        return;
    }
    UIScrollView *list = [[self mainList] list];
    [_largeTitle updateListFill:options
                           list:list
                    inlineInRow:[self inlineInRow:options]
            backgroundComponent:_background != nil];
    // The fill of a push from a lower bar follows the list, which may mount only during the push.
    [_largeTitle updateTitlePlaceFill:options list:list];
}

@end
