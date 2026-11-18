'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
let fcm;
try { fcm = require('./notifications-fcm'); }
catch { fcm = {}; }

test('Firebase sender normalizes escaped private-key newlines and caps multicast batches at 500', async () => {
  assert.equal(typeof fcm.createFirebaseSender, 'function');
  const credentials = [];
  const batches = [];
  const app = {};
  const firebaseAdmin = {
    apps: [],
    getApps: () => firebaseAdmin.apps,
    cert: value => { credentials.push(value); return value; },
    initializeApp: options => { firebaseAdmin.apps.push(app); assert.equal(options.projectId, 'spaceanime-project'); return app; },
    getMessaging: value => {
      assert.equal(value, app);
      return {
        sendEachForMulticast: async message => {
          batches.push(message);
          return { responses: message.tokens.map(() => ({ success: true })) };
        },
      };
    },
  };
  const sender = fcm.createFirebaseSender({
    env: {
      FIREBASE_PROJECT_ID: 'spaceanime-project',
      FIREBASE_CLIENT_EMAIL: 'firebase-admin@spaceanime-project.iam.gserviceaccount.com',
      FIREBASE_PRIVATE_KEY: '-----BEGIN PRIVATE KEY-----\\nline-one\\nline-two\\n-----END PRIVATE KEY-----\\n',
    },
    firebaseAdmin,
  });

  await sender.send(Array.from({ length: 501 }, (_, index) => `token-${index}`), { title: 'Novo lançamento', body: 'Um novo episódio chegou.' });

  assert.equal(credentials.length, 1);
  assert.equal(credentials[0].privateKey, '-----BEGIN PRIVATE KEY-----\nline-one\nline-two\n-----END PRIVATE KEY-----\n');
  assert.deepEqual(batches.map(batch => batch.tokens.length), [500, 1]);
  assert.deepEqual(batches[0].notification, { title: 'Novo lançamento', body: 'Um novo episódio chegou.' });
  assert.equal(firebaseAdmin.getApps().length, 1);
});

test('Firebase sender rejects incomplete configuration without returning a success-shaped fallback', async () => {
  assert.equal(typeof fcm.createFirebaseSender, 'function');
  const sender = fcm.createFirebaseSender({ env: { FIREBASE_PROJECT_ID: 'spaceanime-project' }, firebaseAdmin: {} });
  await assert.rejects(
    () => sender.send(['token-a'], { title: 'Novo', body: 'Lançamento' }),
    error => error.message === 'firebase_not_configured',
  );
});

test('Firebase sender accepts a private key that already contains real newlines', async () => {
  assert.equal(typeof fcm.createFirebaseSender, 'function');
  let credential;
  const firebaseAdmin = {
    apps: [],
    getApps: () => firebaseAdmin.apps,
    cert: value => { credential = value; return value; },
    initializeApp: options => {
      const app = { options };
      firebaseAdmin.apps.push(app);
      return app;
    },
    getMessaging: () => ({ sendEachForMulticast: async message => ({
      responses: message.tokens.map(() => ({ success: true })),
    }) }),
  };
  const privateKey = '-----BEGIN PRIVATE KEY-----\nline-one\nline-two\n-----END PRIVATE KEY-----\n';
  const sender = fcm.createFirebaseSender({
    env: {
      FIREBASE_PROJECT_ID: 'spaceanime-project',
      FIREBASE_CLIENT_EMAIL: 'firebase-admin@spaceanime-project.iam.gserviceaccount.com',
      FIREBASE_PRIVATE_KEY: privateKey,
    },
    firebaseAdmin,
  });

  await sender.send(['token-a'], { title: 'Novo', body: 'Capítulo disponível' });

  assert.equal(credential.privateKey, privateKey);
});

test('Firebase Admin v14 app and messaging entry points are available', () => {
  const appApi = require('firebase-admin/app');
  const messagingApi = require('firebase-admin/messaging');
  assert.equal(typeof appApi.initializeApp, 'function');
  assert.equal(typeof appApi.getApps, 'function');
  assert.equal(typeof appApi.cert, 'function');
  assert.equal(typeof messagingApi.getMessaging, 'function');
});
