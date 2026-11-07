/** Minimal ambient declaration so `process.env.EXPO_PUBLIC_*` type-checks before prebuild */
declare const process: {
  env: Record<string, string | undefined>;
  argv?: string[];
};
