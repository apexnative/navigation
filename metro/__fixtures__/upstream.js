'use strict';

// A transformer the navigation's goes in front of: it gives back what it gets.
module.exports = {
  transform: (args) => args,
  getCacheKey: () => 'upstream',
};
