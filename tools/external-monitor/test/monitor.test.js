'use strict';

/*
 * Zero-dependency test for the external monitor.
 * Stubs global fetch and drives runCycle through: healthy -> outage (one alert)
 * -> still down within cooldown (no new alert) -> recovery (one recovery message).
 */

const assert = require('assert');

// Config must be present before requiring the module (CONFIG is read at import).
process.env.TELEGRAM_BOT_TOKEN = 'test-token';
process.env.TELEGRAM_CHAT_ID = '123';
process.env.WAAD_MONITOR_BACKEND_URL = 'http://backend/health';
process.env.WAAD_MONITOR_FRONTEND_URL = 'http://frontend/';
process.env.WAAD_MONITOR_COOLDOWN_SECONDS = '900';
process.env.WAAD_MONITOR_HEARTBEAT_URL = '';

// Controllable fake world.
const world = { backendOk: true, frontendOk: true };
const telegramMessages = [];

global.fetch = async (url, options) => {
  if (String(url).includes('api.telegram.org')) {
    telegramMessages.push(String(options && options.body));
    return { ok: true, status: 200 };
  }
  if (String(url).includes('backend')) {
    return world.backendOk ? { ok: true, status: 200 } : { ok: false, status: 503 };
  }
  if (String(url).includes('frontend')) {
    return world.frontendOk ? { ok: true, status: 200 } : { ok: false, status: 503 };
  }
  return { ok: true, status: 200 };
};

const { runCycle } = require('../monitor');

function telegramCountContaining(fragment) {
  return telegramMessages.filter((m) => m.includes(encodeURIComponent(fragment)) || m.includes(fragment)).length;
}

(async () => {
  // 1) Everything healthy -> no telegram.
  await runCycle();
  assert.strictEqual(telegramMessages.length, 0, 'healthy cycle must not alert');

  // 2) Backend goes down -> exactly one alert.
  world.backendOk = false;
  await runCycle();
  assert.strictEqual(telegramMessages.length, 1, 'first outage must send exactly one alert');

  // 3) Still down within cooldown -> no additional alert.
  await runCycle();
  assert.strictEqual(telegramMessages.length, 1, 'must not spam within cooldown');

  // 4) Recovery -> exactly one recovery message.
  world.backendOk = true;
  await runCycle();
  assert.strictEqual(telegramMessages.length, 2, 'recovery must send exactly one message');
  const last = telegramMessages[telegramMessages.length - 1];
  assert.ok(/%E2%9C%85|✅|تعاف/.test(last), 'recovery message should be a recovery notice');

  console.log('external-monitor tests passed:', telegramMessages.length, 'telegram messages total');
  process.exit(0);
})().catch((e) => {
  console.error('external-monitor test FAILED:', e.message);
  process.exit(1);
});
