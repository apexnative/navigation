#import <React/RCTAssert.h>

/// Checks a condition the core relies on; in debug a failed check is a React Native red screen, in release a log line.
#define ANNAssert(condition, ...) RCTAssert((condition), __VA_ARGS__)

/// Checks that the code runs on the main queue, where every command and every view of the navigation lives.
#define ANNAssertMainQueue() RCTAssertMainQueue()
