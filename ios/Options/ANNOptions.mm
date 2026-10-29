#import "ANNOptions.h"
#import "ANNOptionsReader.h"
#import <React/RCTLog.h>
#import <objc/runtime.h>

NSDictionary *ANNOptionsDictionary(NSDictionary *dict, NSString *key) {
    if (![dict isKindOfClass:NSDictionary.class]) {
        return nil;
    }
    id value = dict[key];
    return [value isKindOfClass:NSDictionary.class] ? value : nil;
}

// The kinds of property a group holds options in.
static BOOL ANNIsOptionClass(Class cls) {
    return [cls isSubclassOfClass:ANNParam.class] || [cls isSubclassOfClass:ANNOptions.class] ||
           [cls isSubclassOfClass:NSArray.class] || [cls isSubclassOfClass:NSString.class];
}

// The class of an object property from its attributes (`T@"Bool",&,N,V_visible`); nil for any other property.
static Class ANNPropertyClass(objc_property_t property) {
    const char *attributes = property_getAttributes(property);
    if (!attributes || strncmp(attributes, "T@\"", 3) != 0) {
        return nil;
    }
    const char *start = attributes + 3;
    const char *end = strchr(start, '"');
    if (!end) {
        return nil;
    }
    NSString *name = [[NSString alloc] initWithBytes:start length:end - start encoding:NSUTF8StringEncoding];
    return name ? NSClassFromString(name) : nil;
}

#if DEBUG
// There is no iOS test target: in development each group of the core checks once that its table names every property
// that holds options, and that every field of the table has its property (the stand-in for the whole-tree test of
// Android). A missing field would be neither read, merged, copied nor given defaults.
static void ANNCheckFields(Class cls, NSArray<ANNOptionField *> *fields) {
    NSMutableSet<NSString *> *declared = [NSMutableSet new];
    for (ANNOptionField *field in fields) {
        [declared addObject:field.property];
        if (![cls instancesRespondToSelector:NSSelectorFromString(field.property)]) {
            RCTLogError(@"[ApexNavigation] %@ has the field '%@' in its table but no property of that name",
                        NSStringFromClass(cls), field.property);
        }
    }
    NSSet<NSString *> *inherited = [NSSet setWithArray:@[ @"description", @"debugDescription" ]];
    for (Class current = cls; current && current != ANNOptions.class; current = class_getSuperclass(current)) {
        unsigned int count = 0;
        objc_property_t *properties = class_copyPropertyList(current, &count);
        for (unsigned int i = 0; i < count; i++) {
            NSString *name = @(property_getName(properties[i]));
            Class propertyClass = ANNPropertyClass(properties[i]);
            if (propertyClass && ANNIsOptionClass(propertyClass) && ![declared containsObject:name] &&
                ![inherited containsObject:name]) {
                RCTLogError(@"[ApexNavigation] %@.%@ holds options but is not in the field table of its class: it "
                            @"is neither read, merged, copied nor given defaults",
                            NSStringFromClass(cls), name);
            }
        }
        free(properties);
    }
}
#endif

// The fields of each class, asked once. A group may be made on any queue (an empty one), so the table is locked.
static NSArray<ANNOptionField *> *ANNFieldsOf(Class cls) {
    static NSMutableDictionary<NSString *, NSArray<ANNOptionField *> *> *fieldsByClass;
    NSString *name = NSStringFromClass(cls);
    @synchronized(ANNOptions.class) {
        if (!fieldsByClass) {
            fieldsByClass = [NSMutableDictionary new];
        }
        NSArray<ANNOptionField *> *fields = fieldsByClass[name];
        if (!fields) {
            fields = [cls optionFields] ?: @[];
            fieldsByClass[name] = fields;
#if DEBUG
            if (fields.count) {
                ANNCheckFields(cls, fields);
            }
#endif
        }
        return fields;
    }
}

// The properties that hold the options of a group, asked once per class: its field table's, or for a module's group
// (its own read and merge, an empty table) every property of an options kind its classes declare up to `ANNOptions`.
static NSArray<NSString *> *ANNValuePropertiesOf(Class cls) {
    static NSMutableDictionary<NSString *, NSArray<NSString *> *> *propertiesByClass;
    NSString *name = NSStringFromClass(cls);
    @synchronized(ANNOptions.class) {
        if (!propertiesByClass) {
            propertiesByClass = [NSMutableDictionary new];
        }
        NSArray<NSString *> *names = propertiesByClass[name];
        if (names) {
            return names;
        }
    }
    NSArray<ANNOptionField *> *fields = ANNFieldsOf(cls);
    NSMutableArray<NSString *> *found = [NSMutableArray new];
    if (fields.count) {
        for (ANNOptionField *field in fields) {
            [found addObject:field.property];
        }
    } else {
        NSSet<NSString *> *inherited =
            [NSSet setWithArray:@[ @"description", @"debugDescription" ]];
        for (Class current = cls; current && current != ANNOptions.class;
             current = class_getSuperclass(current)) {
            unsigned int count = 0;
            objc_property_t *properties = class_copyPropertyList(current, &count);
            for (unsigned int i = 0; i < count; i++) {
                NSString *property = @(property_getName(properties[i]));
                Class propertyClass = ANNPropertyClass(properties[i]);
                if (propertyClass && ANNIsOptionClass(propertyClass) &&
                    ![inherited containsObject:property] && ![found containsObject:property]) {
                    [found addObject:property];
                }
            }
            free(properties);
        }
    }
    NSArray<NSString *> *names = [found copy];
    @synchronized(ANNOptions.class) {
        propertiesByClass[name] = names;
    }
    return names;
}

BOOL ANNOptionValuesSame(id a, id b) {
    if (a == b) {
        return YES;
    }
    if ([a isKindOfClass:ANNParam.class]) {
        return [b isKindOfClass:ANNParam.class] && [(ANNParam *)a isSameValueAs:b];
    }
    if ([a isKindOfClass:ANNOptions.class]) {
        return [b isKindOfClass:ANNOptions.class] && [(ANNOptions *)a isSameValueAs:b];
    }
    if ([a isKindOfClass:NSArray.class]) {
        if (![b isKindOfClass:NSArray.class] || [a count] != [b count]) {
            return NO;
        }
        NSArray *items = b;
        NSUInteger index = 0;
        for (id item in (NSArray *)a) {
            if (!ANNOptionValuesSame(item, items[index++])) {
                return NO;
            }
        }
        return YES;
    }
    return ANNJSONValuesSame(a, b);
}

NSUInteger ANNOptionValueHash(id value) {
    if ([value isKindOfClass:ANNParam.class]) {
        return [(ANNParam *)value valueHash];
    }
    if ([value isKindOfClass:ANNOptions.class]) {
        return [(ANNOptions *)value valueHash];
    }
    if ([value isKindOfClass:NSArray.class]) {
        NSUInteger hash = [value count];
        for (id item in value) {
            hash = hash * 31 + ANNOptionValueHash(item);
        }
        return hash;
    }
    return ANNJSONValueHash(value);
}

#if DEBUG
// Runs the check on `root` and on every class reached through its group and group-array fields, so a class that only
// an array holds (a button, an action of a fab, a token of the search bar) is checked although no object of it is made.
void ANNCheckOptionClassesFrom(Class root) {
    NSMutableSet<NSString *> *visited = [NSMutableSet new];
    NSMutableArray<Class> *pending = [NSMutableArray arrayWithObject:root];
    while (pending.count) {
        Class cls = pending.lastObject;
        [pending removeLastObject];
        if ([visited containsObject:NSStringFromClass(cls)]) {
            continue;
        }
        [visited addObject:NSStringFromClass(cls)];
        for (ANNOptionField *field in ANNFieldsOf(cls)) {
            if ((field.kind == ANNOptionFieldKindGroup || field.kind == ANNOptionFieldKindGroupArray) &&
                field.groupClass) {
                [pending addObject:field.groupClass];
            }
        }
    }
}
#endif

// A group read under `component` of the parse's path. A group whose reading throws (a module's parse) is empty, and
// the first such failure is kept for the command's error.
static ANNOptions *ANNReadGroup(Class cls, NSDictionary *json, NSString *component) {
    if (!json) {
        return [[cls alloc] initWithDict:nil];
    }
    if (component) {
        [ANNOptionsReader enter:component];
    }
    ANNOptions *group = nil;
    @try {
        group = [[cls alloc] initWithDict:json];
    } @catch (NSException *exception) {
        [ANNOptionsReader failAtKey:nil];
        group = [[cls alloc] initWithDict:nil];
    } @finally {
        if (component) {
            [ANNOptionsReader leave];
        }
    }
    return group;
}

// Each item is a group of its own: two trees never share one.
static NSArray *ANNCopyItems(NSArray *items) {
    NSMutableArray *copies = [NSMutableArray arrayWithCapacity:items.count];
    for (id item in items) {
        [copies addObject:[item isKindOfClass:ANNOptions.class] ? [item copy] : item];
    }
    return copies;
}

@implementation ANNOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[];
}

- (instancetype)init {
    return [self initWithDict:nil];
}

- (instancetype)initWithDict:(NSDictionary *)dict {
    if (!(self = [super init])) {
        return nil;
    }
    NSArray<ANNOptionField *> *fields = ANNFieldsOf(self.class);
    if (!fields.count) {
        return self;
    }
    NSDictionary *json = [dict isKindOfClass:NSDictionary.class] ? dict : nil;
    NSString *reading = nil;
    @try {
        for (ANNOptionField *field in fields) {
            reading = field.key;
            [self readField:field from:json];
        }
    } @catch (NSException *exception) {
        // The group reads as empty; the rest of the tree as usual.
        [ANNOptionsReader failAtKey:reading];
        for (ANNOptionField *field in fields) {
            [self readField:field from:nil];
        }
    }
    return self;
}

- (void)readField:(ANNOptionField *)field from:(NSDictionary *)json {
    NSString *key = field.key;
    id value = key ? json[key] : json;
    id read = nil;
    switch (field.kind) {
    case ANNOptionFieldKindParam:
        read = field.parse(json, key ?: @"");
        break;
    case ANNOptionFieldKindString:
        read = [value isKindOfClass:NSString.class] && (!field.values || [field.values containsObject:value])
                   ? value
                   : nil;
        break;
    case ANNOptionFieldKindGroup:
        read = ANNReadGroup(field.groupClass, [value isKindOfClass:NSDictionary.class] ? value : nil, key);
        break;
    case ANNOptionFieldKindGroupArray:
        read = [self groupsOf:value field:field];
        break;
    case ANNOptionFieldKindStringArray:
        read = [self stringsOf:value field:field];
        break;
    case ANNOptionFieldKindObjectArray:
        read = [self objectsOf:value];
        break;
    }
    [self setValue:read forKey:field.property];
}

- (NSArray *)groupsOf:(id)value field:(ANNOptionField *)field {
    if (![value isKindOfClass:NSArray.class]) {
        return nil;
    }
    NSMutableArray *items = [NSMutableArray new];
    NSUInteger index = 0;
    for (id item in value) {
        if ([item isKindOfClass:NSDictionary.class]) {
            NSString *component = [NSString stringWithFormat:@"%@[%lu]", field.key, (unsigned long)index];
            [items addObject:ANNReadGroup(field.groupClass, item, component)];
        }
        index++;
    }
    return [items copy];
}

- (NSArray<NSString *> *)stringsOf:(id)value field:(ANNOptionField *)field {
    if (![value isKindOfClass:NSArray.class]) {
        return nil;
    }
    NSMutableArray<NSString *> *strings = [NSMutableArray new];
    for (id item in value) {
        if ([item isKindOfClass:NSString.class] && (!field.values || [field.values containsObject:item])) {
            [strings addObject:item];
        }
    }
    return [strings copy];
}

- (NSArray<NSDictionary *> *)objectsOf:(id)value {
    if (![value isKindOfClass:NSArray.class]) {
        return nil;
    }
    NSMutableArray<NSDictionary *> *objects = [NSMutableArray new];
    for (id item in value) {
        if ([item isKindOfClass:NSDictionary.class]) {
            [objects addObject:item];
        }
    }
    return [objects copy];
}

- (void)mergeOptions:(ANNOptions *)options {
    if (!options || options == self) {
        return;
    }
    for (ANNOptionField *field in ANNFieldsOf(self.class)) {
        NSString *property = field.property;
        if (![options respondsToSelector:NSSelectorFromString(property)]) {
            continue;
        }
        id theirs = [options valueForKey:property];
        if (!theirs) {
            continue;
        }
        switch (field.kind) {
        case ANNOptionFieldKindParam:
            if ([theirs hasValue]) {
                [self setValue:theirs forKey:property];
            }
            break;
        case ANNOptionFieldKindGroup: {
            ANNOptions *mine = [self valueForKey:property];
            if (mine) {
                [mine mergeOptions:theirs];
            } else {
                [self setValue:[theirs copy] forKey:property];
            }
            break;
        }
        case ANNOptionFieldKindGroupArray:
            [self setValue:ANNCopyItems(theirs) forKey:property];
            break;
        case ANNOptionFieldKindString:
        case ANNOptionFieldKindStringArray:
        case ANNOptionFieldKindObjectArray:
            [self setValue:theirs forKey:property];
            break;
        }
    }
}

- (BOOL)hasValue {
    for (ANNOptionField *field in ANNFieldsOf(self.class)) {
        id value = [self valueForKey:field.property];
        switch (field.kind) {
        case ANNOptionFieldKindParam:
        case ANNOptionFieldKindGroup:
            if ([value hasValue]) {
                return YES;
            }
            break;
        case ANNOptionFieldKindString:
        case ANNOptionFieldKindGroupArray:
        case ANNOptionFieldKindStringArray:
        case ANNOptionFieldKindObjectArray:
            if (value) {
                return YES;
            }
            break;
        }
    }
    return NO;
}

// Generic over the whole tree, so that any option an edit takes out (`clearOptions`) goes without a list of options:
// the field table names each key's property; a module's group, which reads its JSON itself, names its properties as
// the keys.
- (BOOL)clearValueAtPath:(NSArray<NSString *> *)path {
    NSString *key = path.firstObject;
    if (key.length == 0) {
        return NO;
    }
    NSArray<ANNOptionField *> *fields = ANNFieldsOf(self.class);
    NSString *property = nil;
    if (fields.count) {
        for (ANNOptionField *field in fields) {
            if ([field.key isEqualToString:key]) {
                property = field.property;
                break;
            }
        }
        if (!property) {
            // A group without a key (the font of a text) holds keys of its parent's object.
            for (ANNOptionField *field in fields) {
                if (field.kind == ANNOptionFieldKindGroup && !field.key &&
                    [[self valueForKey:field.property] clearValueAtPath:path]) {
                    return YES;
                }
            }
            return NO;
        }
    } else if ([ANNValuePropertiesOf(self.class) containsObject:key]) {
        property = key;
    } else {
        return NO;
    }
    if (path.count == 1) {
        ANNOptions *fresh = [[self.class alloc] initWithDict:nil];
        [self setValue:[fresh valueForKey:property] forKey:property];
        return YES;
    }
    id value = [self valueForKey:property];
    if (![value isKindOfClass:ANNOptions.class]) {
        return NO;
    }
    NSArray<NSString *> *rest = [path subarrayWithRange:NSMakeRange(1, path.count - 1)];
    return [(ANNOptions *)value clearValueAtPath:rest];
}

- (id)copyWithZone:(NSZone *)zone {
    ANNOptions *copy = [[self.class alloc] initWithDict:nil];
    [copy mergeOptions:self];
    return copy;
}

- (instancetype)copy {
    return [self copyWithZone:nil];
}

- (instancetype)withDefault:(ANNOptions *)defaults {
    ANNOptions *result = defaults ? [defaults copy] : [[self.class alloc] initWithDict:nil];
    [result mergeOptions:self];
    return result;
}

- (BOOL)isSameValueAs:(ANNOptions *)other {
    if (other == self) {
        return YES;
    }
    if (other.class != self.class) {
        return NO;
    }
    for (NSString *property in ANNValuePropertiesOf(self.class)) {
        if (!ANNOptionValuesSame([self valueForKey:property], [other valueForKey:property])) {
            return NO;
        }
    }
    return YES;
}

- (NSUInteger)valueHash {
    NSUInteger hash = (NSUInteger)(__bridge void *)self.class;
    for (NSString *property in ANNValuePropertiesOf(self.class)) {
        hash = hash * 31 + ANNOptionValueHash([self valueForKey:property]);
    }
    return hash;
}

@end
