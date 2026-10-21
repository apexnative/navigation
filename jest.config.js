module.exports = {
  preset: '@react-native/jest-preset',
  transformIgnorePatterns: ['node_modules/(?!(@react-native|react-native)/)'],
  transform: {
    '\\.[jt]sx?$': 'babel-jest',
  },
  roots: ['<rootDir>/src/', '<rootDir>/cli/'],
  testPathIgnorePatterns: ['/node_modules/', '<rootDir>/lib/'],
  modulePathIgnorePatterns: ['<rootDir>/example/node_modules', '<rootDir>/lib/'],
  moduleNameMapper: {
    '^react-native$': '<rootDir>/node_modules/react-native',
  },
  collectCoverageFrom: ['src/**/*.(ts|tsx)', '!src/**/*.test.*'],
  resetMocks: true,
  resetModules: true,
  coverageReporters: ['json', 'lcov', 'text', 'html'],
  testEnvironment: 'node',
};
