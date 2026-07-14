import assert from 'node:assert/strict';
import test from 'node:test';
import {
  consumeOAuthLoginIntent,
  hasOAuthLoginIntent,
  readOAuthLoginIntent,
  rememberOAuthLoginIntent
} from '../src/modules/auth/utils/oauthLoginIntent.js';

const origin = 'https://dev.flyfish.group';
const memoryStorage = () => {
  const values = new Map();
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: key => values.delete(key)
  };
};

test('matches the sponsor return path while ignoring the OAuth cache buster', () => {
  const storage = memoryStorage();
  rememberOAuthLoginIntent(storage, {
    provider: 'github',
    path: '/shop/sponsor?source=github',
    skipProfilePrompt: true
  }, { origin, now: 1000 });

  assert.equal(hasOAuthLoginIntent(storage, 'github',
    '/shop/sponsor?_oauth=123&source=github', { origin, now: 2000 }), true);
});

test('consumes a matching intent exactly once', () => {
  const storage = memoryStorage();
  rememberOAuthLoginIntent(storage, {
    provider: 'github',
    path: '/donate?source=github',
    skipProfilePrompt: true
  }, { origin, now: 1000 });

  const intent = consumeOAuthLoginIntent(storage, '/donate?source=github', { origin, now: 2000 });
  assert.equal(intent?.provider, 'github');
  assert.equal(intent?.skipProfilePrompt, true);
  assert.equal(consumeOAuthLoginIntent(storage, '/donate?source=github', { origin, now: 2000 }), null);
});

test('does not consume an intent from another page', () => {
  const storage = memoryStorage();
  rememberOAuthLoginIntent(storage, {
    provider: 'github',
    path: '/shop/sponsor?source=github'
  }, { origin, now: 1000 });

  assert.equal(consumeOAuthLoginIntent(storage, '/shop/item-list', { origin, now: 2000 }), null);
  assert.equal(readOAuthLoginIntent(storage, { origin, now: 2000 })?.provider, 'github');
});

test('expires stale intents and rejects external return targets', () => {
  const storage = memoryStorage();
  assert.equal(rememberOAuthLoginIntent(storage, {
    provider: 'github',
    path: 'https://example.com/steal-session'
  }, { origin, now: 1000 }), null);

  rememberOAuthLoginIntent(storage, {
    provider: 'github',
    path: '/sponsor?source=github'
  }, { origin, now: 1000 });
  assert.equal(readOAuthLoginIntent(storage, { origin, now: 11 * 60 * 1000 }), null);
});
