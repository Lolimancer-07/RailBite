const { getDefaultConfig } = require('expo/metro-config');
const path = require('path');

const projectRoot = __dirname;
const monorepoRoot = path.resolve(projectRoot, '../..');

const config = getDefaultConfig(projectRoot);

// 1. Watch all files within the monorepo
config.watchFolders = [monorepoRoot];

// 2. Let Metro know where to resolve packages and in what order
config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, 'node_modules'),
  path.resolve(monorepoRoot, 'node_modules'),
];

// 3. Custom module resolution:
// - Redirect 'mqtt' to its browser/React Native pre-bundled distribution
// - Transparently resolve .js relative imports to .ts / .tsx when TS sources are used
const originalResolveRequest = config.resolver.resolveRequest;
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === 'mqtt') {
    return (originalResolveRequest || context.resolveRequest)(context, 'mqtt/dist/mqtt.min.js', platform);
  }
  if (moduleName.endsWith('.js') && (moduleName.startsWith('./') || moduleName.startsWith('../'))) {
    try {
      return (originalResolveRequest || context.resolveRequest)(context, moduleName, platform);
    } catch (err) {
      for (const ext of ['.ts', '.tsx']) {
        try {
          const candidate = moduleName.slice(0, -3) + ext;
          return (originalResolveRequest || context.resolveRequest)(context, candidate, platform);
        } catch {}
      }
      throw err;
    }
  }
  return (originalResolveRequest || context.resolveRequest)(context, moduleName, platform);
};

module.exports = config;
