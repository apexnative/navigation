#import "ANNLayoutReader.h"
#import "ANNArrangementState.h"
#import "ANNErrorTexts.h"
#import "ApexNavigation+Internal.h"

// Where a node stands: what its parent is, for the rules of its place. Nil parent: a root, a modal, an overlay, the
// node of a command, an entry of `setStackRoot`.
@interface ANNLayoutPlace : NSObject
@property(nonatomic) ANNLayoutType parentType;
@property(nonatomic, copy) NSString *parentId;
// The side menu of a side, for a child of that side.
@property(nonatomic, copy) NSString *menuId;
// The column a child of a split view is, `''` when it is none.
@property(nonatomic, copy) NSString *column;
@end

@implementation ANNLayoutPlace
@end

@implementation ANNLayoutReader

#pragma mark - Commands

+ (ANNLayoutNode *)nodeFrom:(id)json error:(NSString **)error {
    return [self read:json place:nil ids:[NSMutableSet new] error:error];
}

// The root, then each modal, then each overlay; one layout for the ids.
+ (ANNLayoutRoot *)rootFrom:(id)json error:(NSString **)error {
    NSDictionary *layout = [json isKindOfClass:NSDictionary.class] ? json : nil;
    id rootJson = layout[@"root"];
    if (![rootJson isKindOfClass:NSDictionary.class]) {
        *error = ANNErrorTexts.rootMissing;
        return nil;
    }
    NSMutableSet<NSString *> *ids = [NSMutableSet new];
    ANNLayoutNode *root = [self read:rootJson place:nil ids:ids error:error];
    if (!root) {
        return nil;
    }
    NSArray<ANNLayoutNode *> *modals = [self readList:layout[@"modals"] ids:ids error:error];
    if (!modals) {
        return nil;
    }
    NSArray<ANNLayoutNode *> *overlays = [self readList:layout[@"overlays"] ids:ids error:error];
    if (!overlays) {
        return nil;
    }
    return [[ANNLayoutRoot alloc] initWithRoot:root modals:modals overlays:overlays];
}

+ (NSArray<ANNLayoutNode *> *)nodesFrom:(id)json error:(NSString **)error {
    return [self readList:json ids:[NSMutableSet new] error:error];
}

// A list that is missing or no array is empty.
+ (NSArray<ANNLayoutNode *> *)readList:(id)json
                                   ids:(NSMutableSet<NSString *> *)ids
                                 error:(NSString **)error {
    if (![json isKindOfClass:NSArray.class]) {
        return @[];
    }
    NSMutableArray<ANNLayoutNode *> *nodes = [NSMutableArray new];
    for (id item in (NSArray *)json) {
        ANNLayoutNode *node = [self read:item place:nil ids:ids error:error];
        if (!node) {
            return nil;
        }
        [nodes addObject:node];
    }
    return [nodes copy];
}

#pragma mark - One node

// One pass, depth first, in the order both platforms check: the node's type and id, its place under its parent, its id
// against the others, a screen's name and registration, its rules on the number of children, each child whole, then its
// rules over the children's types. The first error is the text.
+ (ANNLayoutNode *)read:(id)json
                  place:(ANNLayoutPlace *)place
                    ids:(NSMutableSet<NSString *> *)ids
                  error:(NSString **)error {
    // 1. A known type: a node that is no object, or has no string type, gives ''.
    NSDictionary *object = [json isKindOfClass:NSDictionary.class] ? json : nil;
    id typeName = object[@"type"];
    ANNLayoutType type = ANNLayoutTypeFromName(typeName);
    if (type == ANNLayoutTypeUnknown) {
        BOOL named = [typeName isKindOfClass:NSString.class];
        *error = [ANNErrorTexts unknownNodeType:named ? typeName : @""];
        return nil;
    }
    NSString *name = ANNLayoutTypeName(type);
    // 2. The id, taken as it is.
    id nodeIdValue = object[@"id"];
    if (![nodeIdValue isKindOfClass:NSString.class] || [(NSString *)nodeIdValue length] == 0) {
        *error = [ANNErrorTexts nodeWithoutId:name];
        return nil;
    }
    NSString *nodeId = nodeIdValue;
    // 3. The place.
    NSString *text = [self checkPlaceOf:type nodeId:nodeId place:place];
    // 4. One id goes to one node of the layout; the ids of the sides count too (JS makes them unique).
    if (!text && [ids containsObject:nodeId]) {
        text = [ANNErrorTexts idGivenTwice:nodeId];
    }
    [ids addObject:nodeId];
    // `data` and `children` of the wrong kind count as missing.
    NSDictionary *data = [object[@"data"] isKindOfClass:NSDictionary.class] ? object[@"data"] : @{};
    BOOL screen = type == ANNLayoutTypeComponent || type == ANNLayoutTypeExternalComponent;
    id childrenValue = object[@"children"];
    NSArray *childrenJson = [childrenValue isKindOfClass:NSArray.class] ? childrenValue : @[];
    // 5. A screen; its children are never read.
    if (!text && screen) {
        text = [self checkScreen:type nodeId:nodeId data:data];
    }
    // 6. The number of children.
    if (!text && !screen) {
        text = [self checkCountOf:type nodeId:nodeId count:childrenJson.count place:place];
    }
    if (text) {
        *error = text;
        return nil;
    }
    // 7. Each child whole.
    NSMutableArray<ANNLayoutNode *> *children = [NSMutableArray new];
    if (!screen) {
        NSArray<NSString *> *columns = type == ANNLayoutTypeSplitView
                                           ? [ANNLayoutNode columnNamesOfData:data
                                                                   childCount:childrenJson.count]
                                           : nil;
        for (NSUInteger index = 0; index < childrenJson.count; index++) {
            ANNLayoutPlace *childPlace = [ANNLayoutPlace new];
            childPlace.parentType = type;
            childPlace.parentId = nodeId;
            BOOL inSide = place.parentType == ANNLayoutTypeSideMenuRoot;
            childPlace.menuId = inSide ? place.parentId : nil;
            childPlace.column = columns[index];
            ANNLayoutNode *child = [self read:childrenJson[index]
                                        place:childPlace
                                          ids:ids
                                        error:error];
            if (!child) {
                return nil;
            }
            [children addObject:child];
        }
    }
    ANNLayoutNode *node = [[ANNLayoutNode alloc] initWithNodeId:nodeId
                                                           type:type
                                                           data:data
                                                       children:children];
    // 8. The children's types.
    text = [self checkChildTypesOf:node];
    if (text) {
        *error = text;
        return nil;
    }
    return node;
}

#pragma mark - The rules

// What the parent takes in this place: a "no" of the table is a rejection, a case it leaves undescribed is not. Then a
// side stands only directly under a side menu.
+ (NSString *)checkPlaceOf:(ANNLayoutType)type
                    nodeId:(NSString *)nodeId
                     place:(ANNLayoutPlace *)place {
    NSString *typeName = ANNLayoutTypeName(type);
    BOOL screen = type == ANNLayoutTypeComponent || type == ANNLayoutTypeExternalComponent;
    BOOL side = type == ANNLayoutTypeSideMenuLeft || type == ANNLayoutTypeSideMenuCenter ||
                type == ANNLayoutTypeSideMenuRight;
    if (place) {
        NSString *parentId = place.parentId;
        switch (place.parentType) {
        case ANNLayoutTypeStack:
            if (!screen && type != ANNLayoutTypeArrangement) {
                return [ANNErrorTexts stackTakesScreens:parentId type:typeName];
            }
            break;
        case ANNLayoutTypeBottomTabs:
            if (type == ANNLayoutTypeBottomTabs) {
                return [ANNErrorTexts tabsInTabs:parentId];
            }
            break;
        case ANNLayoutTypeSideMenuRoot:
            if (!side) {
                return [ANNErrorTexts sideMenuChild:parentId type:typeName];
            }
            break;
        case ANNLayoutTypeSideMenuLeft:
        case ANNLayoutTypeSideMenuCenter:
        case ANNLayoutTypeSideMenuRight:
            if (type == ANNLayoutTypeSideMenuRoot) {
                return [ANNErrorTexts sideMenuInSide:place.menuId ?: @""];
            }
            break;
        case ANNLayoutTypeSplitView:
            if (place.column.length > 0 && type == ANNLayoutTypeSplitView) {
                return [ANNErrorTexts splitViewInColumn:parentId];
            }
            break;
        case ANNLayoutTypeArrangement:
            if (!screen) {
                return [ANNErrorTexts pairNeedsTwoScreens:parentId];
            }
            break;
        default:
            break;
        }
    }
    if (side && place.parentType != ANNLayoutTypeSideMenuRoot) {
        return [ANNErrorTexts sideOutsideMenu:nodeId type:typeName];
    }
    return nil;
}

// A `Component` named `slot:<Name>` is a screen like any other here. A native screen's name must be registered:
// the registry has its own lock, as an app may register from any thread.
+ (NSString *)checkScreen:(ANNLayoutType)type nodeId:(NSString *)nodeId data:(NSDictionary *)data {
    NSString *name = [data[@"name"] isKindOfClass:NSString.class] ? data[@"name"] : nil;
    if (name.length == 0) {
        return [ANNErrorTexts nodeWithoutName:ANNLayoutTypeName(type) nodeId:nodeId];
    }
    BOOL external = type == ANNLayoutTypeExternalComponent;
    if (external && ![ApexNavigation hasNativeScreenNamed:name]) {
        return [ANNErrorTexts nativeScreenNotRegistered:name];
    }
    return nil;
}

// iOS shows more than five tabs (the More tab on iPhone): only Android rejects them. A pair needs iOS 27.1 and the
// SDK it came with; it is checked first, as no pair shows without it.
+ (NSString *)checkCountOf:(ANNLayoutType)type
                    nodeId:(NSString *)nodeId
                     count:(NSUInteger)count
                     place:(ANNLayoutPlace *)place {
    switch (type) {
    case ANNLayoutTypeArrangement:
        if (!ANNArrangementState.canShowArrangements) {
            return [ANNErrorTexts pairUnsupported:nodeId];
        }
        return count != 2 ? [ANNErrorTexts pairNeedsTwoScreens:nodeId] : nil;
    case ANNLayoutTypeStack:
        return count == 0 ? [ANNErrorTexts emptyStack:nodeId] : nil;
    case ANNLayoutTypeBottomTabs:
        return count == 0 ? [ANNErrorTexts emptyTabs:nodeId] : nil;
    case ANNLayoutTypeSideMenuLeft:
    case ANNLayoutTypeSideMenuCenter:
    case ANNLayoutTypeSideMenuRight:
        // A side outside a side menu failed on its place already.
        if (count != 1) {
            return [ANNErrorTexts sideChildCount:nodeId menuId:place.parentId ?: @""];
        }
        return nil;
    default:
        return nil;
    }
}

// One center, at most one left and one right; a primary and a secondary column.
+ (NSString *)checkChildTypesOf:(ANNLayoutNode *)node {
    if (node.type == ANNLayoutTypeSideMenuRoot) {
        NSUInteger centers = 0, lefts = 0, rights = 0;
        for (ANNLayoutNode *side in node.children) {
            centers += side.type == ANNLayoutTypeSideMenuCenter;
            lefts += side.type == ANNLayoutTypeSideMenuLeft;
            rights += side.type == ANNLayoutTypeSideMenuRight;
        }
        if (centers != 1 || lefts > 1 || rights > 1) {
            return [ANNErrorTexts sideMenuSides:node.nodeId];
        }
    }
    if (node.type == ANNLayoutTypeSplitView) {
        NSDictionary<NSString *, ANNLayoutNode *> *columns = node.columnChildren;
        if (!columns[@"primary"] || !columns[@"secondary"]) {
            return [ANNErrorTexts splitViewColumns:node.nodeId];
        }
    }
    return nil;
}

@end
