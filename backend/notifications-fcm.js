'use strict';

const BATCH_SIZE = 500;

function createFirebaseSender({ env = process.env, firebaseAdmin } = {}) {
  let appApi = firebaseAdmin;
  let messagingApi = firebaseAdmin;
  let messaging;

  function getMessaging() {
    const projectId = env.FIREBASE_PROJECT_ID?.trim();
    const clientEmail = env.FIREBASE_CLIENT_EMAIL?.trim();
    const privateKey = env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n');
    if (!projectId || !clientEmail || !privateKey?.trim() ||
        !privateKey.includes('-----BEGIN PRIVATE KEY-----') ||
        !privateKey.includes('-----END PRIVATE KEY-----')) {
      throw new Error('firebase_not_configured');
    }
    if (!appApi) {
      appApi = require('firebase-admin/app');
      messagingApi = require('firebase-admin/messaging');
    }
    if (messaging) return messaging;
    try {
      const app = appApi.getApps().find(candidate => candidate.name === '[DEFAULT]') ||
        appApi.initializeApp({
            credential: appApi.cert({ projectId, clientEmail, privateKey }),
            projectId,
          });
      if (app.options?.projectId && app.options.projectId !== projectId) throw new Error();
      messaging = messagingApi.getMessaging(app);
      return messaging;
    } catch {
      throw new Error('firebase_initialization_failed');
    }
  }

  return {
    async send(tokens, notification) {
      if (!Array.isArray(tokens) || tokens.length === 0 || tokens.length > 10000 ||
          tokens.some(token => typeof token !== 'string' || token.length < 1 || token.length > 4096 || /[\u0000-\u0020\u007f]/.test(token)) ||
          !notification || typeof notification.title !== 'string' || typeof notification.body !== 'string') {
        throw new Error('firebase_invalid_message');
      }
      const uniqueTokens = [...new Set(tokens)];
      const client = getMessaging();
      const responses = [];
      for (let offset = 0; offset < uniqueTokens.length; offset += BATCH_SIZE) {
        let result;
        try {
          result = await client.sendEachForMulticast({
            tokens: uniqueTokens.slice(offset, offset + BATCH_SIZE),
            notification: {
              title: notification.title.slice(0, 100),
              body: notification.body.slice(0, 300),
            },
          });
        } catch {
          throw new Error('firebase_send_failed');
        }
        if (!result || !Array.isArray(result.responses)) throw new Error('firebase_send_failed');
        responses.push(...result.responses.map(response => ({
          success: response?.success === true,
          errorCode: typeof response?.error?.code === 'string' ? response.error.code : null,
        })));
      }
      return responses;
    },
  };
}

module.exports = { createFirebaseSender };
