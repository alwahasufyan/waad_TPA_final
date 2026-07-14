#!/usr/bin/env node
/*
 * WAAD External Monitor (MON-BKP-LOG-1)
 * -------------------------------------
 * A standalone watchdog that runs OUTSIDE the WAAD backend so it can still
 * alert over Telegram when the backend/frontend is completely down.
 *
 * It polls the configured targets on an interval, sends one Telegram alert per
 * outage (respecting a cooldown), sends exactly one recovery message when a
 * target comes back, and pings the backend heartbeat endpoint so the admin UI
 * can show that an external watchdog is alive.
 *
 * No third-party dependencies — uses the Node 18+ global fetch.
 */

'use strict';

const env = process.env;

function required(name) {
  const value = env[name];
  if (!value || String(value).trim() === '') {
    console.error(`[external-monitor] missing required env: ${name}`);
    process.exit(1);
  }
  return String(value).trim();
}

function optional(name, fallback) {
  const value = env[name];
  return value === undefined || String(value).trim() === '' ? fallback : String(value).trim();
}

const CONFIG = {
  backendUrl: optional('WAAD_MONITOR_BACKEND_URL', 'http://localhost:8081/actuator/health'),
  frontendUrl: optional('WAAD_MONITOR_FRONTEND_URL', 'http://localhost:3001'),
  // Optional: a URL that reflects DB health (e.g. actuator/health/db). Skipped if empty.
  dbHealthUrl: optional('WAAD_MONITOR_DB_HEALTH_URL', ''),
  telegramToken: required('TELEGRAM_BOT_TOKEN'),
  telegramChatId: required('TELEGRAM_CHAT_ID'),
  telegramThreadId: optional('TELEGRAM_ALERT_THREAD_ID', optional('TELEGRAM_THREAD_ID', '')),
  intervalSeconds: parseInt(optional('WAAD_MONITOR_INTERVAL_SECONDS', '60'), 10),
  cooldownSeconds: parseInt(optional('WAAD_MONITOR_COOLDOWN_SECONDS', '900'), 10),
  requestTimeoutMs: parseInt(optional('WAAD_MONITOR_TIMEOUT_MS', '10000'), 10),
  environment: optional('WAAD_MONITOR_ENVIRONMENT', 'production'),
  // Where to POST the heartbeat so the WAAD UI shows the watchdog is alive.
  heartbeatUrl: optional('WAAD_MONITOR_HEARTBEAT_URL', ''),
  heartbeatToken: optional('WAAD_MONITOR_HEARTBEAT_TOKEN', '')
};

// Per-target alert state: { down: bool, lastAlertAt: epochMs }
const state = new Map();

function targetList() {
  const targets = [
    { key: 'backend', label: 'الخدمة الخلفية (Backend)', url: CONFIG.backendUrl },
    { key: 'frontend', label: 'الواجهة (Frontend)', url: CONFIG.frontendUrl }
  ];
  if (CONFIG.dbHealthUrl) {
    targets.push({ key: 'database', label: 'قاعدة البيانات', url: CONFIG.dbHealthUrl });
  }
  return targets;
}

async function httpCheck(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), CONFIG.requestTimeoutMs);
  try {
    const res = await fetch(url, { method: 'GET', signal: controller.signal, redirect: 'manual' });
    // Any non-5xx (and non network error) counts as reachable.
    return { ok: res.status < 500, status: res.status };
  } catch (e) {
    return { ok: false, status: 0, error: e.name === 'AbortError' ? 'timeout' : e.message };
  } finally {
    clearTimeout(timer);
  }
}

async function sendTelegram(text) {
  const url = `https://api.telegram.org/bot${CONFIG.telegramToken}/sendMessage`;
  const body = new URLSearchParams({ chat_id: CONFIG.telegramChatId, text });
  if (CONFIG.telegramThreadId) {
    body.set('message_thread_id', CONFIG.telegramThreadId);
  }
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString()
    });
    if (!res.ok) {
      console.error(`[external-monitor] telegram send failed: HTTP ${res.status}`);
    }
  } catch (e) {
    console.error(`[external-monitor] telegram send error: ${e.message}`);
  }
}

async function postHeartbeat(status) {
  if (!CONFIG.heartbeatUrl) return;
  try {
    const headers = { 'Content-Type': 'application/json' };
    if (CONFIG.heartbeatToken) headers['X-Monitor-Token'] = CONFIG.heartbeatToken;
    await fetch(CONFIG.heartbeatUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify({ source: 'external-monitor', status })
    });
  } catch (e) {
    // Heartbeat failures are non-fatal — the whole point is that the backend may be down.
    console.error(`[external-monitor] heartbeat failed: ${e.message}`);
  }
}

function nowMs() {
  return Date.now();
}

async function runCycle() {
  const targets = targetList();
  let anyDown = false;

  for (const target of targets) {
    const prev = state.get(target.key) || { down: false, lastAlertAt: 0 };
    const result = await httpCheck(target.url);

    if (!result.ok) {
      anyDown = true;
      const sinceLast = nowMs() - prev.lastAlertAt;
      const cooldownPassed = sinceLast >= CONFIG.cooldownSeconds * 1000;
      if (!prev.down || cooldownPassed) {
        await sendTelegram(
          `🚨 WAAD مراقب خارجي\n` +
            `تعذّر الوصول إلى: ${target.label}\n` +
            `الرابط: ${target.url}\n` +
            `الحالة: ${result.status || 'لا استجابة'}${result.error ? ' (' + result.error + ')' : ''}\n` +
            `البيئة: ${CONFIG.environment}\n` +
            `الوقت: ${new Date().toISOString()}`
        );
        state.set(target.key, { down: true, lastAlertAt: nowMs() });
      } else {
        state.set(target.key, { down: true, lastAlertAt: prev.lastAlertAt });
      }
      console.error(`[external-monitor] DOWN ${target.key} (${result.status || result.error})`);
    } else {
      if (prev.down) {
        await sendTelegram(
          `✅ WAAD مراقب خارجي — تعافى\n` +
            `عاد للعمل: ${target.label}\n` +
            `الرابط: ${target.url}\n` +
            `البيئة: ${CONFIG.environment}\n` +
            `الوقت: ${new Date().toISOString()}`
        );
      }
      state.set(target.key, { down: false, lastAlertAt: 0 });
      console.log(`[external-monitor] UP ${target.key} (${result.status})`);
    }
  }

  await postHeartbeat(anyDown ? 'DEGRADED' : 'UP');
}

async function main() {
  console.log(
    `[external-monitor] starting; interval=${CONFIG.intervalSeconds}s cooldown=${CONFIG.cooldownSeconds}s ` +
      `backend=${CONFIG.backendUrl} frontend=${CONFIG.frontendUrl}`
  );
  // Fail fast on obviously bad config.
  if (!Number.isFinite(CONFIG.intervalSeconds) || CONFIG.intervalSeconds < 10) {
    console.error('[external-monitor] WAAD_MONITOR_INTERVAL_SECONDS must be >= 10');
    process.exit(1);
  }
  // Run immediately, then on the interval.
  await runCycle();
  setInterval(() => {
    runCycle().catch((e) => console.error(`[external-monitor] cycle error: ${e.message}`));
  }, CONFIG.intervalSeconds * 1000);
}

// Export internals for tests; only auto-run when invoked directly.
module.exports = { httpCheck, runCycle, CONFIG, state, sendTelegram };

if (require.main === module) {
  main().catch((e) => {
    console.error(`[external-monitor] fatal: ${e.message}`);
    process.exit(1);
  });
}
