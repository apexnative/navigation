#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The `type` of a node of the layout JSON. A name the contract doesn't have reads as `ANNLayoutTypeUnknown`, which no
/// valid layout holds.
typedef NS_ENUM(NSInteger, ANNLayoutType) {
    ANNLayoutTypeUnknown = -1,
    ANNLayoutTypeComponent = 0,
    ANNLayoutTypeExternalComponent,
    ANNLayoutTypeStack,
    ANNLayoutTypeBottomTabs,
    ANNLayoutTypeSideMenuRoot,
    ANNLayoutTypeSideMenuLeft,
    ANNLayoutTypeSideMenuCenter,
    ANNLayoutTypeSideMenuRight,
    ANNLayoutTypeSplitView,
    ANNLayoutTypeArrangement,
};

/// The names JS sends, in the order of `ANNLayoutType`.
NS_INLINE NSArray<NSString *> *ANNLayoutTypeNames(void) {
    return @[
        @"Component", @"ExternalComponent", @"Stack", @"BottomTabs", @"SideMenuRoot",
        @"SideMenuLeft", @"SideMenuCenter", @"SideMenuRight", @"SplitView", @"Arrangement"
    ];
}

/// The type a name stands for; `ANNLayoutTypeUnknown` for any other value, a non-string too.
NS_INLINE ANNLayoutType ANNLayoutTypeFromName(id _Nullable name) {
    if (![name isKindOfClass:NSString.class]) {
        return ANNLayoutTypeUnknown;
    }
    NSUInteger index = [ANNLayoutTypeNames() indexOfObject:name];
    return index == NSNotFound ? ANNLayoutTypeUnknown : (ANNLayoutType)index;
}

/// The name JS gives the type; an empty string for `ANNLayoutTypeUnknown`.
NS_INLINE NSString *ANNLayoutTypeName(ANNLayoutType type) {
    NSArray<NSString *> *names = ANNLayoutTypeNames();
    return type >= 0 && (NSUInteger)type < names.count ? names[(NSUInteger)type] : @"";
}

NS_ASSUME_NONNULL_END
