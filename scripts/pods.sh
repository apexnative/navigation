#!/bin/bash
# pod install for the Showcase, after a native or spec change on iOS. Prints OK, or the end of the error.
source "$(dirname "$0")/lib/quiet.sh"
quiet pods tail yarn example pods
