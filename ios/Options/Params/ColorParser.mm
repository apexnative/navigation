#import "ColorParser.h"
#import "ANNOptionsReader.h"
#import <React/RCTConvert.h>
#import <React/RCTLog.h>

// A system color of UIKit by its name, as React Native names them: `systemBackground` or `systemBackgroundColor`.
// Only a class method of UIColor that takes no argument and gives a color counts.
static UIColor *ANNSystemColorNamed(NSString *name) {
    NSString *selectorName = [name hasSuffix:@"Color"] ? name : [name stringByAppendingString:@"Color"];
    SEL selector = NSSelectorFromString(selectorName);
    if (![UIColor respondsToSelector:selector]) {
        return nil;
    }
    NSMethodSignature *signature = [UIColor methodSignatureForSelector:selector];
    if (signature.numberOfArguments != 2 || signature.methodReturnType[0] != '@') {
        return nil;
    }
    IMP implementation = [UIColor methodForSelector:selector];
    id color = ((id (*)(id, SEL))implementation)(UIColor.class, selector);
    return [color isKindOfClass:UIColor.class] ? color : nil;
}

// `{ semantic: [...] }`: the first name that exists, the app's asset catalog first, then UIKit.
static UIColor *ANNSemanticColor(id names) {
    NSArray *list = [names isKindOfClass:NSString.class] ? @[ names ] : names;
    if (![list isKindOfClass:NSArray.class]) {
        return nil;
    }
    for (id name in list) {
        if (![name isKindOfClass:NSString.class]) {
            continue;
        }
        UIColor *color = [UIColor colorNamed:name] ?: ANNSystemColorNamed(name);
        if (color) {
            return color;
        }
    }
    return nil;
}

// One color: an ARGB number in sRGB through React Native's converter, or a platform color.
static UIColor *ANNOneColor(id value) {
    if (ANNJSONIsNumber(value)) {
        return [RCTConvert UIColor:value];
    }
    if ([value isKindOfClass:NSDictionary.class] && value[@"semantic"]) {
        return ANNSemanticColor(value[@"semantic"]);
    }
    return nil;
}

// A pair comes as `{ light, dark }`, either side may be left out; an app's own `DynamicColorIOS` as
// `{ dynamic: { light, dark } }`. Anything else is not a pair.
static NSDictionary *ANNColorPair(id value) {
    if (![value isKindOfClass:NSDictionary.class]) {
        return nil;
    }
    id dynamic = value[@"dynamic"];
    if (dynamic) {
        return [dynamic isKindOfClass:NSDictionary.class] ? dynamic : nil;
    }
    for (id side in value) {
        if (![side isEqual:@"light"] && ![side isEqual:@"dark"]) {
            return nil;
        }
    }
    return value;
}

@implementation ColorParser

+ (Color *)parse:(NSDictionary *)json key:(NSString *)key {
    id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
    if (!value) {
        return [[Color alloc] initWithValue:nil];
    }
    if (value == NSNull.null || [value isEqual:@"NoColor"]) {
        return Color.noColor;
    }
    UIColor *color = ANNOneColor(value);
    if (color) {
        return [[Color alloc] initWithColor:color];
    }
    NSString *path = [ANNOptionsReader pathOfKey:key];
    NSDictionary *pair = ANNColorPair(value);
    if (pair) {
        UIColor *light = [self side:pair[@"light"] name:@"light" path:path];
        UIColor *dark = [self side:pair[@"dark"] name:@"dark" path:path];
        if (light || dark) {
            return [[Color alloc] initWithLight:light dark:dark];
        }
        return [[Color alloc] initWithValue:nil];
    }
    RCTLogWarn(@"[ApexNavigation] the color of '%@' can't be read, so it is left out: %@", path, value);
    return [[Color alloc] initWithValue:nil];
}

// A side of `{ light, dark }`. A side that is not there is a partial pair and is silent (the sides are optional); a
// side that is there but can't be read is missing too, with a warning.
+ (UIColor *)side:(id)value name:(NSString *)name path:(NSString *)path {
    if (!value || value == NSNull.null) {
        return nil;
    }
    UIColor *color = ANNOneColor(value);
    if (!color) {
        RCTLogWarn(@"[ApexNavigation] the %@ side of the color of '%@' can't be read, so that style shows the "
                   @"platform's color: %@",
                   name, path, value);
    }
    return color;
}

@end
