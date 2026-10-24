#import "Color.h"

@implementation Color

- (instancetype)initWithColor:(UIColor *)color {
    return [super initWithValue:color];
}

- (instancetype)initWithLight:(UIColor *)light dark:(UIColor *)dark {
    UIColor *both = nil;
    if (light && dark) {
        both = [UIColor colorWithDynamicProvider:^UIColor *(UITraitCollection *traits) {
          return traits.userInterfaceStyle == UIUserInterfaceStyleDark ? dark : light;
        }];
    }
    if (self = [super initWithValue:both]) {
        _light = light;
        _dark = dark;
    }
    return self;
}

+ (Color *)noColor {
    return [[Color alloc] initWithValue:NSNull.null];
}

- (BOOL)hasValue {
    return self.value != nil || _light != nil || _dark != nil;
}

- (BOOL)isNoColor {
    return self.isNull;
}

- (BOOL)isPartial {
    return self.value == nil && (_light != nil || _dark != nil);
}

- (UIColor *)get {
    return [super get];
}

- (BOOL)isSameValueAs:(ANNParam *)other {
    if (other == self) {
        return YES;
    }
    if (other.class != self.class) {
        return NO;
    }
    Color *color = (Color *)other;
    if (_light || _dark || color.light || color.dark) {
        return ANNJSONValuesSame(_light, color.light) && ANNJSONValuesSame(_dark, color.dark);
    }
    return [super isSameValueAs:other];
}

- (NSUInteger)valueHash {
    if (_light || _dark) {
        return _light.hash * 31 + _dark.hash;
    }
    return [super valueHash];
}

- (UIColor *)withDefault:(UIColor *)defaultValue {
    if (!self.isPartial) {
        return self.hasValue ? self.get : defaultValue;
    }
    if (!defaultValue) {
        return nil;
    }
    UIColor *light = _light;
    UIColor *dark = _dark;
    return [UIColor colorWithDynamicProvider:^UIColor *(UITraitCollection *traits) {
      UIColor *side = traits.userInterfaceStyle == UIUserInterfaceStyleDark ? dark : light;
      return side ?: [defaultValue resolvedColorWithTraitCollection:traits];
    }];
}

- (UIColor *)colorForTraitCollection:(UITraitCollection *)traitCollection {
    if (_light || _dark) {
        return traitCollection.userInterfaceStyle == UIUserInterfaceStyleDark ? _dark : _light;
    }
    return [self.get resolvedColorWithTraitCollection:traitCollection];
}

@end
