const num = (name, fallback) => {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') return fallback;
  const v = Number(raw);
  if (!Number.isFinite(v)) throw new Error(`${name} must be a number, got "${raw}"`);
  if (v < 0) throw new Error(`${name} must be >= 0, got "${raw}"`);
  return v;
};

const str = (name, fallback) => {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') return fallback;
  return raw;
};

const requiredStr = (name) => {
  const raw = __ENV[name];
  if (raw === undefined || raw === '') {
    throw new Error(`${name} is required; it is injected by loadtest/run.sh from the seeded dataset`);
  }
  return raw;
};

const userUuid = (userIndex) => {
  // Deterministic mapping: VU -> seed user id in the 10000000-0000-4000-8000- namespace
  // userIndex is 1-based: ((vuId-1) % seedUsers) + 1
  const suffix = String(userIndex).padStart(12, '0');
  return `10000000-0000-4000-8000-${suffix}`;
};

export const config = {
  baseUrl: str('BASE_URL', 'http://app:8080'),
  testId: str('TEST_ID', 'local'),
  vus: num('LOAD_VUS', 1000),
  seedUsers: num('SEED_USERS', 1000),
  thinkTimeSeconds: num('LOAD_THINK_TIME_SECONDS', 10),
  bodySampleEvery: num('LOAD_BODY_SAMPLE_EVERY', 10),
  rampSeconds: num('LOAD_RAMP_SECONDS', 120),
  steadySeconds: num('LOAD_STEADY_SECONDS', 600),
  rampDownSeconds: num('LOAD_RAMPDOWN_SECONDS', 30),
  smoke: str('SMOKE', '0') === '1',
  targets: {
    availP95Ms: num('TARGET_AVAIL_P95_MS', 300),
    availP99Ms: num('TARGET_AVAIL_P99_MS', 1000),
    manipP95Ms: num('TARGET_MANIP_P95_MS', 500),
    errorRate: num('TARGET_ERROR_RATE', 0.01),
    mixTolerance: num('MIX_TOLERANCE', 0.01),
  },
  window: {
    from: requiredStr('WINDOW_FROM'),
    to: requiredStr('WINDOW_TO'),
  },
};

// Defense in depth: the harness enforces LOAD_VUS <= SEED_USERS. A script that runs directly
// without the harness should still fail fast, clearly, rather than producing a tsunami of 403s.
if (config.vus > config.seedUsers) {
  throw new Error(
    `LOAD_VUS (${config.vus}) > SEED_USERS (${config.seedUsers}) — a VU would map to a non-existent user identity (expected 1..SEED_USERS).`,
  );
}

export { userUuid };
