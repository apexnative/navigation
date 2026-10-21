#import "ANNLayoutNode.h"

// The column names of a split view; any other name is left out.
static NSSet<NSString *> *ANNKnownColumnNames(void) {
    static NSSet<NSString *> *names;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      names = [NSSet
          setWithArray:@[ @"primary", @"secondary", @"supplementary", @"compact", @"inspector" ]];
    });
    return names;
}

// Only strings: anything else in a list of names counts as missing.
static NSArray<NSString *> *ANNStrings(id value) {
    if (![value isKindOfClass:NSArray.class]) {
        return @[];
    }
    NSMutableArray<NSString *> *strings = [NSMutableArray new];
    for (id item in (NSArray *)value) {
        if ([item isKindOfClass:NSString.class]) {
            [strings addObject:item];
        }
    }
    return [strings copy];
}

@implementation ANNLayoutNode

- (instancetype)initWithNodeId:(NSString *)nodeId
                          type:(ANNLayoutType)type
                          data:(NSDictionary<NSString *, id> *)data
                      children:(NSArray<ANNLayoutNode *> *)children {
    if (self = [super init]) {
        _nodeId = [nodeId copy];
        _type = type;
        _data = [data copy];
        _children = [children copy];
    }
    return self;
}

- (NSString *)typeName {
    return ANNLayoutTypeName(_type);
}

- (NSString *)description {
    return [NSString stringWithFormat:@"<%@ '%@'>", self.typeName, _nodeId];
}

#pragma mark - Fields of data

- (NSString *)stringOf:(NSString *)field {
    id value = _data[field];
    return [value isKindOfClass:NSString.class] ? value : nil;
}

- (NSString *)name {
    return [self stringOf:@"name"];
}

- (NSDictionary<NSString *, id> *)options {
    id options = _data[@"options"];
    return [options isKindOfClass:NSDictionary.class] ? options : @{};
}

- (NSString *)key {
    return [self stringOf:@"key"];
}

// The props of a React screen stay in JS: only a native screen's are read.
- (NSDictionary<NSString *, id> *)passProps {
    if (_type != ANNLayoutTypeExternalComponent) {
        return nil;
    }
    id props = _data[@"passProps"];
    return [props isKindOfClass:NSDictionary.class] ? props : nil;
}

- (NSArray<NSString *> *)functions {
    return _type == ANNLayoutTypeExternalComponent ? ANNStrings(_data[@"functions"]) : @[];
}

// A non-string entry counts as an empty name, so that `columns[i]` still names child i.
- (NSArray<NSString *> *)columns {
    id columns = _data[@"columns"];
    if (![columns isKindOfClass:NSArray.class]) {
        return @[];
    }
    NSMutableArray<NSString *> *names = [NSMutableArray new];
    for (id column in (NSArray *)columns) {
        [names addObject:[column isKindOfClass:NSString.class] ? column : @""];
    }
    return [names copy];
}

+ (NSArray<NSString *> *)columnNamesOfData:(NSDictionary<NSString *, id> *)data
                                childCount:(NSUInteger)childCount {
    id columns = data[@"columns"];
    NSMutableArray<NSString *> *names = [NSMutableArray new];
    // One meaning on both platforms: without `columns` (missing, no array, or empty) the first two children are the
    // primary and the secondary column.
    if (![columns isKindOfClass:NSArray.class] || [(NSArray *)columns count] == 0) {
        columns = @[ @"primary", @"secondary" ];
    }
    NSMutableSet<NSString *> *taken = [NSMutableSet new];
    for (NSUInteger index = 0; index < childCount; index++) {
        id column = index < [(NSArray *)columns count] ? ((NSArray *)columns)[index] : nil;
        BOOL named = [column isKindOfClass:NSString.class] &&
                     [ANNKnownColumnNames() containsObject:column] &&
                     ![taken containsObject:column];
        if (named) {
            [taken addObject:column];
        }
        [names addObject:named ? column : @""];
    }
    return [names copy];
}

- (NSDictionary<NSString *, ANNLayoutNode *> *)columnChildren {
    if (_type != ANNLayoutTypeSplitView) {
        return @{};
    }
    NSArray<NSString *> *names = [ANNLayoutNode columnNamesOfData:_data childCount:_children.count];
    NSMutableDictionary<NSString *, ANNLayoutNode *> *children = [NSMutableDictionary new];
    for (NSUInteger index = 0; index < names.count; index++) {
        if (names[index].length > 0) {
            children[names[index]] = _children[index];
        }
    }
    return [children copy];
}

#pragma mark - Kinds

- (BOOL)isScreen {
    return _type == ANNLayoutTypeComponent || _type == ANNLayoutTypeExternalComponent;
}

- (BOOL)isSideMenuSide {
    return _type == ANNLayoutTypeSideMenuLeft || _type == ANNLayoutTypeSideMenuCenter ||
           _type == ANNLayoutTypeSideMenuRight;
}

- (NSArray<ANNLayoutNode *> *)allNodes {
    NSMutableArray<ANNLayoutNode *> *nodes = [NSMutableArray arrayWithObject:self];
    for (ANNLayoutNode *child in _children) {
        [nodes addObjectsFromArray:child.allNodes];
    }
    return [nodes copy];
}

@end
