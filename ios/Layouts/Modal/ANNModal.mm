#import "ANNModal.h"
#import "ANNModalCoordinator.h"
#import "ANNParking.h"
#import "ANNSheetPresenter.h"
#import <React/RCTUtils.h>

@implementation ANNModalDismissal
@end

@implementation ANNModal

- (instancetype)initWithRoot:(UIViewController *)root rootId:(NSString *)rootId {
    if (self = [super init]) {
        _root = root;
        _rootId = [rootId copy];
        _state = ANNModalStateWaiting;
        _stateBeforeRoot = ANNModalStateWaiting;
        _ids = [NSSet set];
        _parking = [ANNParking new];
        _pending = [NSMutableArray new];
    }
    return self;
}

#pragma mark - UIAdaptivePresentationControllerDelegate

// The adaptive style of a popover stays UIKit's: the popover module answers it for `adaptive: false`.

- (void)presentationControllerWillDismiss:(UIPresentationController *)presentationController {
    [ANNModalCoordinator modalWillBeDraggedAway:self];
}

// After a zoom UIKit tells it from a block of its animation queue: the close, which stops the modal's React surfaces,
// runs on the main queue.
- (void)presentationControllerDidDismiss:(UIPresentationController *)presentationController {
    if (RCTIsMainQueue()) {
        [ANNModalCoordinator modalWasClosedByUser:self];
        return;
    }
    __weak ANNModal *weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
      [ANNModalCoordinator modalWasClosedByUser:weakSelf];
    });
}

- (void)presentationControllerDidAttemptToDismiss:
    (UIPresentationController *)presentationController {
    [ANNModalCoordinator userTriedToClose:self];
}

#pragma mark - UISheetPresentationControllerDelegate

// UIKit tells only of the detents the finger chose.
- (void)sheetPresentationControllerDidChangeSelectedDetentIdentifier:
    (UISheetPresentationController *)sheetPresentationController {
    UISheetPresentationControllerDetentIdentifier identifier =
        sheetPresentationController.selectedDetentIdentifier;
    id detent = identifier ? [ANNSheetPresenter detentForIdentifier:identifier] : nil;
    if (detent) {
        [ANNModalCoordinator modal:self detentChanged:detent];
    }
}

@end
