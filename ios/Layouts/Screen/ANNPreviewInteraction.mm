#import "ANNPreviewInteraction.h"
#import "UIViewController+LayoutProtocol.h"
#import <objc/runtime.h>

static const void *ANNPreviewInteractionKey = &ANNPreviewInteractionKey;

// Every preview until it goes (detach), which destroys its screen: its React view stops first. Kept
// by its view only, it would go with the view (after a reload React Native drops the views of the
// old screens) and take its screen along, with the React view running.
static NSMutableSet *ANNPreviews(void) {
    static NSMutableSet *previews;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      previews = [NSMutableSet new];
    });
    return previews;
}

// Kept by the source screen: when the screen goes, its previews go too.
@interface ANNPreviewSourceLifetime : NSObject
@property(nonatomic, weak) ANNPreviewInteraction *interaction;
@end

@implementation ANNPreviewInteraction {
    UIViewController *_preview;
    NSInteger _reactTag;
    BOOL (^_prepare)(void);
    BOOL (^_commit)(void);
    void (^_dismissed)(void);
    UIContextMenuInteraction *_interaction;
}

+ (void)attachPreview:(UIViewController *)preview
               toView:(UIView *)view
             reactTag:(NSInteger)reactTag
               source:(UIViewController *)source
              prepare:(BOOL (^)(void))prepare
               commit:(BOOL (^)(void))commit
            dismissed:(void (^)(void))dismissed {
    if (!view || !preview) {
        if (dismissed) {
            dismissed();
        }
        return;
    }
    [objc_getAssociatedObject(view, ANNPreviewInteractionKey) detach];
    ANNPreviewInteraction *previewInteraction = [ANNPreviewInteraction new];
    previewInteraction->_preview = preview;
    previewInteraction->_reactTag = reactTag;
    previewInteraction->_prepare = [prepare copy];
    previewInteraction->_commit = [commit copy];
    previewInteraction->_dismissed = [dismissed copy];
    previewInteraction->_interaction =
        [[UIContextMenuInteraction alloc] initWithDelegate:previewInteraction];
    [view addInteraction:previewInteraction->_interaction];
    // The interaction keeps its delegate weakly: the view keeps it.
    objc_setAssociatedObject(view, ANNPreviewInteractionKey, previewInteraction,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    [ANNPreviews() addObject:previewInteraction];
    if (source) {
        ANNPreviewSourceLifetime *lifetime = [ANNPreviewSourceLifetime new];
        lifetime.interaction = previewInteraction;
        // One per preview: a screen can preview from several views.
        objc_setAssociatedObject(source, (__bridge const void *)previewInteraction, lifetime,
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
}

// The previews of the JavaScript before a reload left the set with it: an id the new one counted again finds its own.
+ (BOOL)dismissPreviewOfScreen:(NSString *)componentId {
    for (ANNPreviewInteraction *preview in ANNPreviews().allObjects) {
        if ([preview->_preview.layoutInfo.componentId isEqualToString:componentId]) {
            [preview detach];
            return YES;
        }
    }
    return NO;
}

+ (NSArray<UIViewController *> *)removeAllPreviewsAfterReload {
    NSMutableArray<UIViewController *> *screens = [NSMutableArray new];
    for (ANNPreviewInteraction *preview in ANNPreviews().allObjects) {
        if (preview->_preview) {
            [screens addObject:preview->_preview];
        }
        preview->_dismissed = nil;
        [preview detach];
    }
    return screens;
}

- (void)detach {
    UIView *view = _interaction.view;
    [view removeInteraction:_interaction];
    // A tap's commit animation may still run: a preview that went, its screen destroyed and its push failed, is not
    // pushed when the animation ends.
    _prepare = nil;
    _commit = nil;
    void (^dismissed)(void) = _dismissed;
    _dismissed = nil;
    if (dismissed) {
        dismissed();
    }
    // Last: the preview may go now.
    if (view && objc_getAssociatedObject(view, ANNPreviewInteractionKey) == self) {
        objc_setAssociatedObject(view, ANNPreviewInteractionKey, nil,
                                 OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    }
    [ANNPreviews() removeObject:self];
}

#pragma mark - UIContextMenuInteractionDelegate

- (UIContextMenuConfiguration *)contextMenuInteraction:(UIContextMenuInteraction *)interaction
                        configurationForMenuAtLocation:(CGPoint)location {
    // Fabric recycles component views: a view that shows another component has another tag now.
    // A preview already shown as a screen is no preview any more.
    if (interaction.view.tag != _reactTag || _preview.parentViewController ||
        _preview.presentingViewController) {
        [self detach];
        return nil;
    }
    // UIKit asks for the configuration as the press starts, about half a second before it shows the preview: the
    // screen renders now, so that its content is there by then.
    if (_prepare && !_prepare()) {
        [self detach];
        return nil;
    }
    UIViewController *preview = _preview;
    NSArray *actions = preview.resolveOptions.preview.actions;
    return [UIContextMenuConfiguration
        configurationWithIdentifier:nil
                    previewProvider:^UIViewController * {
                      return preview;
                    }
                     actionProvider:^UIMenu *(NSArray<UIMenuElement *> *suggestedActions) {
                       return [UIMenu menuWithTitle:@""
                                           children:[self menuElementsOf:actions preview:preview]];
                     }];
}

- (void)contextMenuInteraction:(UIContextMenuInteraction *)interaction
    willPerformPreviewActionForMenuWithConfiguration:(UIContextMenuConfiguration *)configuration
                                            animator:(id<UIContextMenuInteractionCommitAnimating>)
                                                         animator {
    BOOL commit = [_preview.resolveOptionsWithDefault.preview.commit withDefault:NO];
    // With `commit` the preview grows into the screen; without it, it goes away.
    animator.preferredCommitStyle =
        commit ? UIContextMenuInteractionCommitStylePop : UIContextMenuInteractionCommitStyleDismiss;
    [animator addCompletion:^{
      if (self->_commit && self->_commit()) {
          // Shown as a screen: the push is done, nothing was dismissed.
          self->_dismissed = nil;
          [self detach];
      }
    }];
}

#pragma mark - Targeted previews

// The view the menu lifts from and goes back to: a snapshot of the source view in its window. With
// the source view itself UIKit adds its views next to it, among the React views of its parent, and
// Fabric then mounts and unmounts at the wrong places.
- (UITargetedPreview *)snapshotPreviewOf:(UIView *)view {
    UIWindow *window = view.window;
    UIView *snapshot = [view snapshotViewAfterScreenUpdates:NO];
    if (!window || !snapshot) {
        return nil;
    }
    UIPreviewParameters *parameters = [UIPreviewParameters new];
    parameters.backgroundColor = UIColor.clearColor;
    parameters.visiblePath = [UIBezierPath bezierPathWithRoundedRect:view.bounds
                                                        cornerRadius:view.layer.cornerRadius];
    CGPoint center = [view convertPoint:CGPointMake(CGRectGetMidX(view.bounds),
                                                    CGRectGetMidY(view.bounds))
                                 toView:window];
    UIPreviewTarget *target = [[UIPreviewTarget alloc] initWithContainer:window center:center];
    return [[UITargetedPreview alloc] initWithView:snapshot parameters:parameters target:target];
}

- (UITargetedPreview *)contextMenuInteraction:(UIContextMenuInteraction *)interaction
                                configuration:(UIContextMenuConfiguration *)configuration
        highlightPreviewForItemWithIdentifier:(id<NSCopying>)identifier API_AVAILABLE(ios(16.0)) {
    return [self snapshotPreviewOf:interaction.view];
}

- (UITargetedPreview *)contextMenuInteraction:(UIContextMenuInteraction *)interaction
                                configuration:(UIContextMenuConfiguration *)configuration
        dismissalPreviewForItemWithIdentifier:(id<NSCopying>)identifier API_AVAILABLE(ios(16.0)) {
    return [self snapshotPreviewOf:interaction.view];
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wdeprecated-implementations"
// iOS 15.
- (UITargetedPreview *)contextMenuInteraction:(UIContextMenuInteraction *)interaction
    previewForHighlightingMenuWithConfiguration:(UIContextMenuConfiguration *)configuration {
    return [self snapshotPreviewOf:interaction.view];
}

- (UITargetedPreview *)contextMenuInteraction:(UIContextMenuInteraction *)interaction
    previewForDismissingMenuWithConfiguration:(UIContextMenuConfiguration *)configuration {
    return [self snapshotPreviewOf:interaction.view];
}
#pragma clang diagnostic pop

#pragma mark - Actions

// `preview.actions` of the previewed screen: `actions` of an action make a submenu, `style`
// 'destructive' a red item and 'selected' a checked one. A press sends navigationButtonPressed
// for the previewed screen with the id of the action.
- (NSArray<UIMenuElement *> *)menuElementsOf:(NSArray *)actions preview:(UIViewController *)preview {
    NSMutableArray<UIMenuElement *> *elements = [NSMutableArray new];
    for (NSDictionary *action in actions) {
        if (![action isKindOfClass:NSDictionary.class]) {
            continue;
        }
        NSString *title = [action[@"title"] isKindOfClass:NSString.class] ? action[@"title"] : @"";
        NSArray *subactions = [action[@"actions"] isKindOfClass:NSArray.class] ? action[@"actions"] : nil;
        if (subactions.count > 0) {
            [elements addObject:[UIMenu menuWithTitle:title
                                             children:[self menuElementsOf:subactions
                                                                   preview:preview]]];
            continue;
        }
        NSString *actionId = action[@"id"];
        __weak UIViewController *weakPreview = preview;
        UIAction *item = [UIAction actionWithTitle:title
                                             image:nil
                                        identifier:nil
                                           handler:^(__kindof UIAction *_Nonnull menuAction) {
                                             UIViewController *previewed = weakPreview;
                                             [previewed.eventEmitter
                                                 sendOnNavigationButtonPressed:previewed.layoutInfo
                                                                                   .componentId
                                                                      buttonId:actionId];
                                           }];
        if ([action[@"style"] isEqual:@"destructive"]) {
            item.attributes = UIMenuElementAttributesDestructive;
        } else if ([action[@"style"] isEqual:@"selected"]) {
            item.state = UIMenuElementStateOn;
        }
        [elements addObject:item];
    }
    return elements;
}

@end

@implementation ANNPreviewSourceLifetime

- (void)dealloc {
    ANNPreviewInteraction *interaction = self.interaction;
    if (!interaction) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
      [interaction detach];
    });
}

@end
