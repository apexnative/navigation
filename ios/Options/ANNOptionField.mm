#import "ANNOptionField.h"
#import "ANNFontOptions.h"
#import "BoolParser.h"
#import "ColorParser.h"
#import "DoubleParser.h"
#import "ImageParser.h"
#import "NumberParser.h"
#import "Param.h"
#import "TextParser.h"

@implementation ANNOptionField

- (instancetype)initWithKey:(NSString *)key
                   property:(NSString *)property
                       kind:(ANNOptionFieldKind)kind
                 groupClass:(Class)groupClass
                     values:(NSSet<NSString *> *)values
                      parse:(ANNOptionParamParse)parse {
    if (self = [super init]) {
        _key = [key copy];
        _property = [property copy];
        _kind = kind;
        _groupClass = groupClass;
        _values = [values copy];
        _parse = [parse copy];
    }
    return self;
}

+ (instancetype)field:(NSString *)key kind:(ANNOptionFieldKind)kind {
    return [[self alloc] initWithKey:key property:key kind:kind groupClass:nil values:nil parse:nil];
}

#pragma mark - Values

+ (instancetype)paramField:(NSString *)key parse:(ANNOptionParamParse)parse {
    return [[self alloc] initWithKey:key
                            property:key
                                kind:ANNOptionFieldKindParam
                          groupClass:nil
                              values:nil
                               parse:parse];
}

+ (instancetype)boolField:(NSString *)key {
    return [self paramField:key
                      parse:^ANNParam *(NSDictionary *json, NSString *jsonKey) {
                        return [BoolParser parse:json key:jsonKey];
                      }];
}

+ (instancetype)doubleField:(NSString *)key {
    return [self paramField:key
                      parse:^ANNParam *(NSDictionary *json, NSString *jsonKey) {
                        return [DoubleParser parse:json key:jsonKey];
                      }];
}

+ (instancetype)durationField:(NSString *)key {
    return [self paramField:key
                      parse:^ANNParam *(NSDictionary *json, NSString *jsonKey) {
                        return [DoubleParser parseDuration:json key:jsonKey];
                      }];
}

+ (instancetype)numberField:(NSString *)key {
    return [self paramField:key
                      parse:^ANNParam *(NSDictionary *json, NSString *jsonKey) {
                        return [NumberParser parse:json key:jsonKey];
                      }];
}

+ (instancetype)textField:(NSString *)key {
    return [self paramField:key
                      parse:^ANNParam *(NSDictionary *json, NSString *jsonKey) {
                        return [TextParser parse:json key:jsonKey];
                      }];
}

+ (instancetype)enumField:(NSString *)key values:(NSArray<NSString *> *)values {
    NSSet<NSString *> *allowed = [NSSet setWithArray:values];
    ANNOptionField *field =
        [self paramField:key
                   parse:^ANNParam *(NSDictionary *json, NSString *jsonKey) {
                     return [TextParser parse:json key:jsonKey values:allowed];
                   }];
    field->_values = allowed;
    return field;
}

+ (instancetype)colorField:(NSString *)key {
    return [self paramField:key
                      parse:^ANNParam *(NSDictionary *json, NSString *jsonKey) {
                        return [ColorParser parse:json key:jsonKey];
                      }];
}

+ (instancetype)imageField:(NSString *)key {
    return [self paramField:key
                      parse:^ANNParam *(NSDictionary *json, NSString *jsonKey) {
                        return [ImageParser parse:json key:jsonKey];
                      }];
}

+ (instancetype)paramField:(NSString *)key check:(ANNOptionValueCheck)check {
    return [self paramField:key
                      parse:^ANNParam *(NSDictionary *json, NSString *jsonKey) {
                        id value = [json isKindOfClass:NSDictionary.class] ? json[jsonKey] : nil;
                        return [[Param alloc] initWithValue:value && check(value) ? value : nil];
                      }];
}

+ (instancetype)stringField:(NSString *)key values:(NSArray<NSString *> *)values {
    return [[self alloc] initWithKey:key
                            property:key
                                kind:ANNOptionFieldKindString
                          groupClass:nil
                              values:values ? [NSSet setWithArray:values] : nil
                               parse:nil];
}

#pragma mark - Groups and arrays

+ (instancetype)groupField:(NSString *)key class:(Class)groupClass {
    return [[self alloc] initWithKey:key
                            property:key
                                kind:ANNOptionFieldKindGroup
                          groupClass:groupClass
                              values:nil
                               parse:nil];
}

+ (instancetype)fontField {
    return [[self alloc] initWithKey:nil
                            property:@"font"
                                kind:ANNOptionFieldKindGroup
                          groupClass:ANNFontOptions.class
                              values:nil
                               parse:nil];
}

+ (instancetype)groupArrayField:(NSString *)key class:(Class)itemClass {
    return [[self alloc] initWithKey:key
                            property:key
                                kind:ANNOptionFieldKindGroupArray
                          groupClass:itemClass
                              values:nil
                               parse:nil];
}

+ (instancetype)stringArrayField:(NSString *)key values:(NSArray<NSString *> *)values {
    return [[self alloc] initWithKey:key
                            property:key
                                kind:ANNOptionFieldKindStringArray
                          groupClass:nil
                              values:values ? [NSSet setWithArray:values] : nil
                               parse:nil];
}

+ (instancetype)objectArrayField:(NSString *)key {
    return [self field:key kind:ANNOptionFieldKindObjectArray];
}

- (instancetype)withProperty:(NSString *)property {
    return [[ANNOptionField alloc] initWithKey:_key
                                      property:property
                                          kind:_kind
                                    groupClass:_groupClass
                                        values:_values
                                         parse:_parse];
}

@end
