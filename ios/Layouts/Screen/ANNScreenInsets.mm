#import "ANNScreenInsets.h"

NSNotificationName const ANNScreenSafeAreaPaddingDidChangeNotification = @"ANNScreenSafeAreaPaddingDidChange";

static NSMutableDictionary<NSString *, NSDictionary<NSString *, NSNumber *> *> *ANNScreenInsetsById;
// Main queue only.
static NSMutableDictionary<NSString *, NSValue *> *ANNScreenPaddingById;

@implementation ANNScreenInsets

+ (void)initialize {
    if (self == ANNScreenInsets.class) {
        ANNScreenInsetsById = [NSMutableDictionary new];
        ANNScreenPaddingById = [NSMutableDictionary new];
    }
}

+ (void)setInsets:(UIEdgeInsets)insets
          padding:(UIEdgeInsets)padding
      forScreenId:(NSString *)screenId
     eventEmitter:(ANNEventEmitter *)eventEmitter {
    NSValue *paddingValue = [NSValue valueWithUIEdgeInsets:padding];
    if (![ANNScreenPaddingById[screenId] isEqualToValue:paddingValue]) {
        ANNScreenPaddingById[screenId] = paddingValue;
        [NSNotificationCenter.defaultCenter postNotificationName:ANNScreenSafeAreaPaddingDidChangeNotification
                                                          object:screenId];
    }
    // JavaScript gets what still covers the content: the edges the safe area view keeps it off are 0.
    NSDictionary<NSString *, NSNumber *> *value = @{
        @"top" : @(MAX(insets.top - padding.top, 0)),
        @"right" : @(MAX(insets.right - padding.right, 0)),
        @"bottom" : @(MAX(insets.bottom - padding.bottom, 0)),
        @"left" : @(MAX(insets.left - padding.left, 0)),
    };
    @synchronized(ANNScreenInsetsById) {
        if ([ANNScreenInsetsById[screenId] isEqualToDictionary:value]) {
            return;
        }
        ANNScreenInsetsById[screenId] = value;
    }
    [eventEmitter sendScreenInsetsChanged:screenId insets:value];
}

+ (NSDictionary<NSString *, NSNumber *> *)insetsForScreenId:(NSString *)screenId {
    @synchronized(ANNScreenInsetsById) {
        return ANNScreenInsetsById[screenId];
    }
}

+ (UIEdgeInsets)paddingForScreenId:(NSString *)screenId {
    return ANNScreenPaddingById[screenId].UIEdgeInsetsValue;
}

+ (void)removeScreenId:(NSString *)screenId {
    [ANNScreenPaddingById removeObjectForKey:screenId];
    @synchronized(ANNScreenInsetsById) {
        [ANNScreenInsetsById removeObjectForKey:screenId];
    }
}

+ (void)removeAll {
    [ANNScreenPaddingById removeAllObjects];
    @synchronized(ANNScreenInsetsById) {
        [ANNScreenInsetsById removeAllObjects];
    }
}

@end
