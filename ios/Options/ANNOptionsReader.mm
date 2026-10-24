#import "ANNOptionsReader.h"
#import "ANNAssert.h"
#import "ANNNavigationOptions.h"
#if DEBUG
#import <React/RCTLog.h>
#endif

// The parse runs on the main queue, one tree at a time: the path and the first failure are its state. A group read
// outside a parse (a module that reads its own JSON) finds an empty path and keeps no failure.
static NSMutableArray<NSString *> *ANNPathComponents;
static NSString *ANNFirstFailure;

#if DEBUG
// A deep mutable copy of `json` with `value` at `path` (keys, and indexes of arrays); nil `value` removes the key.
static id ANNJSONWith(id json, NSArray *path, id value) {
    if (path.count == 0) {
        return value;
    }
    NSArray *rest = [path subarrayWithRange:NSMakeRange(1, path.count - 1)];
    if ([json isKindOfClass:NSArray.class]) {
        NSMutableArray *items = [json mutableCopy];
        NSUInteger index = [path[0] unsignedIntegerValue];
        items[index] = ANNJSONWith(items[index], rest, value);
        return items;
    }
    NSMutableDictionary *fields = [json mutableCopy];
    id inner = ANNJSONWith(fields[path[0]], rest, value);
    if (inner) {
        fields[path[0]] = inner;
    } else {
        [fields removeObjectForKey:path[0]];
    }
    return fields;
}

// There is no iOS test target: in development the value equality of the options (`isSameValueAs:`) checks itself
// once on a tree with a value of each kind: two reads, a copy and a merge give the same options and the same hash, and
// a change of one value at any depth gives other options. A module's group (the menu) compares by its properties.
static void ANNCheckSameValues(void) {
    NSDictionary *button = @{
        @"id" : @"save",
        @"text" : @"Save",
        @"icon" : @{@"system" : @"star"},
        @"enabled" : @YES,
        @"badge" : @1,
        @"fontSize" : @15,
        @"menu" : @{
            @"title" : @"More",
            @"items" : @[ @{@"id" : @"details", @"title" : @"Details", @"checked" : @YES} ],
        },
    };
    NSDictionary *json = @{
        @"topBar" : @{
            @"title" : @{@"text" : @"Home", @"color" : @(0xFF112233)},
            @"rightButtonColor" :
                @{@"dynamic" : @{@"light" : @(0xFF000000), @"dark" : @(0xFFFFFFFF)}},
            @"leftButtonColor" : NSNull.null,
            @"rightButtons" : @[ button ],
            @"leftButtons" : @[],
            @"height" : @44,
        },
        @"keyCommands" : @[ @{@"input" : @"k", @"modifiers" : @[ @"command" ]} ],
    };
    ANNNavigationOptions *options = [[ANNNavigationOptions alloc] initWithDict:json];
    ANNNavigationOptions *again = [[ANNNavigationOptions alloc] initWithDict:json];
    ANNNavigationOptions *copy = [options copy];
    ANNNavigationOptions *overEmpty = [options withDefault:[ANNNavigationOptions new]];
    for (ANNNavigationOptions *same in @[ again, copy, overEmpty ]) {
        if (![options isSameValueAs:same] || options.valueHash != same.valueHash) {
            RCTLogError(@"[ApexNavigation] options read, copied or resolved from the same JSON are "
                        @"not the same value");
        }
    }
    // Each change: the path of one value and its new value; an unset key is not the same as `null` or `[]`.
    NSDictionary<NSString *, NSArray *> *changes = @{
        @"a text" : @[ @[ @"topBar", @"title", @"text" ], @"Profile" ],
        @"a color" : @[ @[ @"topBar", @"title", @"color" ], @(0xFF112234) ],
        @"a side of a color pair" :
            @[ @[ @"topBar", @"rightButtonColor", @"dynamic", @"dark" ], @(0xFFEEEEEE) ],
        @"a number" : @[ @[ @"topBar", @"height" ], @45 ],
        @"an image" : @[ @[ @"topBar", @"rightButtons", @0, @"icon", @"system" ], @"heart" ],
        @"a boolean" : @[ @[ @"topBar", @"rightButtons", @0, @"enabled" ], @NO ],
        @"a boolean for a number" : @[ @[ @"topBar", @"rightButtons", @0, @"badge" ], @YES ],
        @"a font" : @[ @[ @"topBar", @"rightButtons", @0, @"fontSize" ], @16 ],
        @"a module's group" :
            @[ @[ @"topBar", @"rightButtons", @0, @"menu", @"items", @0, @"checked" ], @NO ],
        @"an object of an array" : @[ @[ @"keyCommands", @0, @"modifiers", @0 ], @"shift" ],
    };
    NSDictionary<NSString *, NSArray *> *removals = @{
        @"a color of `null` and no color" : @[ @"topBar", @"leftButtonColor" ],
        @"an empty array and no array" : @[ @"topBar", @"leftButtons" ],
    };
    NSMutableDictionary<NSString *, id> *variants = [NSMutableDictionary new];
    [changes enumerateKeysAndObjectsUsingBlock:^(NSString *what, NSArray *change, BOOL *stop) {
      variants[what] = ANNJSONWith(json, change[0], change[1]);
    }];
    [removals enumerateKeysAndObjectsUsingBlock:^(NSString *what, NSArray *path, BOOL *stop) {
      variants[what] = ANNJSONWith(json, path, nil);
    }];
    [variants enumerateKeysAndObjectsUsingBlock:^(NSString *what, NSDictionary *variant,
                                                  BOOL *stop) {
      ANNNavigationOptions *changed = [[ANNNavigationOptions alloc] initWithDict:variant];
      if ([options isSameValueAs:changed] || [changed isSameValueAs:options]) {
          RCTLogError(@"[ApexNavigation] options that differ in %@ are the same value", what);
      }
    }];
    ANNNavigationOptions *merged = [options copy];
    [merged mergeOptions:[[ANNNavigationOptions alloc]
                             initWithDict:@{@"topBar" : @{@"title" : @{@"text" : @"Profile"}}}]];
    ANNNavigationOptions *read = [[ANNNavigationOptions alloc]
        initWithDict:ANNJSONWith(json, @[ @"topBar", @"title", @"text" ], @"Profile")];
    if (![merged isSameValueAs:read] || merged.valueHash != read.valueHash) {
        RCTLogError(@"[ApexNavigation] merged options are not the same value as the same options "
                    @"read at once");
    }
}
#endif

@implementation ANNOptionsReader

+ (ANNNavigationOptions *)optionsFrom:(NSDictionary *)json unreadablePath:(NSString **)unreadablePath {
    ANNAssertMainQueue();
#if DEBUG
    static dispatch_once_t checkOnce;
    dispatch_once(&checkOnce, ^{
      ANNCheckOptionClassesFrom(ANNNavigationOptions.class);
      ANNCheckSameValues();
    });
#endif
    NSMutableArray<NSString *> *outerPath = ANNPathComponents;
    NSString *outerFailure = ANNFirstFailure;
    ANNPathComponents = [NSMutableArray new];
    ANNFirstFailure = nil;
    ANNNavigationOptions *options = nil;
    @try {
        options = [[ANNNavigationOptions alloc] initWithDict:[json isKindOfClass:NSDictionary.class] ? json : nil];
    } @catch (NSException *exception) {
        [self failAtKey:nil];
        options = [[ANNNavigationOptions alloc] initWithDict:nil];
    }
    if (unreadablePath) {
        *unreadablePath = ANNFirstFailure;
    }
    ANNPathComponents = outerPath;
    ANNFirstFailure = outerFailure;
    return options;
}

+ (NSString *)pathOfKey:(NSString *)key {
    NSArray<NSString *> *components = ANNPathComponents ?: @[];
    if (key.length) {
        components = [components arrayByAddingObject:key];
    }
    return [components componentsJoinedByString:@"."];
}

+ (void)enter:(NSString *)component {
    [ANNPathComponents addObject:component];
}

+ (void)leave {
    if (ANNPathComponents.count) {
        [ANNPathComponents removeLastObject];
    }
}

+ (void)failAtKey:(NSString *)key {
    if (ANNPathComponents && !ANNFirstFailure) {
        ANNFirstFailure = [self pathOfKey:key];
    }
}

@end
