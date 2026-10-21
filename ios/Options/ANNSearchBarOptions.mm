#import "ANNSearchBarOptions.h"

@implementation ANNSearchBarOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"visible"],
        [ANNOptionField boolField:@"focus"],
        [ANNOptionField boolField:@"hideOnScroll"],
        [ANNOptionField boolField:@"hideTopBarOnFocus"],
        [ANNOptionField boolField:@"obscuresBackgroundDuringPresentation"],
        [ANNOptionField colorField:@"backgroundColor"],
        [ANNOptionField colorField:@"tintColor"],
        [ANNOptionField textField:@"placeholder"],
        [ANNOptionField textField:@"text"],
        [ANNOptionField paramField:@"placement"
                             parse:^ANNParam *(NSDictionary *json, NSString *key) {
                               NSDictionary<NSString *, NSNumber *> *placements = @{
                                   @"automatic" : @(SearchBarPlacementAutomatic),
                                   @"stacked" : @(SearchBarPlacementStacked),
                                   @"integrated" : @(SearchBarPlacementIntegrated),
                                   @"integratedButton" : @(SearchBarPlacementIntegratedButton),
                                   @"integratedCentered" : @(SearchBarPlacementIntegratedCentered),
                               };
                               id value = [json isKindOfClass:NSDictionary.class] ? json[key] : nil;
                               NSNumber *placement = [value isKindOfClass:NSString.class] ? placements[value] : nil;
                               return [[ANNIntegerParam alloc] initWithValue:placement];
                             }],
        [ANNOptionField groupArrayField:@"scopes" class:ANNSearchScopeOptions.class],
        [ANNOptionField textField:@"selectedScope"],
        [ANNOptionField enumField:@"scopeBarActivation"
                           values:@[ @"automatic", @"onTextEntry", @"onSearchActivation" ]],
        [ANNOptionField groupArrayField:@"tokens" class:ANNSearchTokenOptions.class],
        [ANNOptionField groupArrayField:@"suggestions" class:ANNSearchSuggestionOptions.class],
        [ANNOptionField groupField:@"resultsComponent" class:ANNComponentOptions.class],
        [ANNOptionField enumField:@"resultsActivation" values:@[ @"onSearchActivation", @"onTextEntry" ]],
        [ANNOptionField textField:@"testID"],
    ];
}

@end
