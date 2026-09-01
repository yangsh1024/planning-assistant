const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

function loadPage(post, wx, setTimeout) {
  let page;
  const source = fs.readFileSync(path.join(__dirname, 'web-login.js'), 'utf8');
  new Function('require', 'Page', 'wx', 'setTimeout', source)(
    () => ({ get: () => {}, post }),
    (definition) => { page = definition; },
    wx,
    setTimeout,
  );
  return page;
}

test('successful approval returns the user to the miniapp home page', async () => {
  const calls = { post: [], switchTab: [] };
  const page = loadPage(
    (url, data) => {
      calls.post.push({ url, data });
      return Promise.resolve();
    },
    {
      showToast: () => {},
      switchTab: (options) => calls.switchTab.push(options),
    },
    (callback) => callback(),
  );

  page.approve.call({ data: { requestId: 'request-123' } });
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(calls.post, [{ url: '/web-auth/requests/request-123/approve', data: {} }]);
  assert.deepEqual(calls.switchTab, [{ url: '/pages/home/home' }]);
});

test('cancelling a login request returns the user to the miniapp home page', async () => {
  const calls = { post: [], switchTab: [] };
  const page = loadPage(
    (url, data) => {
      calls.post.push({ url, data });
      return Promise.resolve();
    },
    {
      navigateBack: () => {},
      switchTab: (options) => calls.switchTab.push(options),
    },
    (callback) => callback(),
  );

  page.reject.call({ data: { requestId: 'request-123' } });
  await new Promise((resolve) => setImmediate(resolve));

  assert.deepEqual(calls.post, [{ url: '/web-auth/requests/request-123/reject', data: {} }]);
  assert.deepEqual(calls.switchTab, [{ url: '/pages/home/home' }]);
});

test('cancelling before a login request is resolved returns the user to the miniapp home page', () => {
  const calls = { switchTab: [] };
  const page = loadPage(
    () => Promise.resolve(),
    {
      navigateBack: () => {},
      switchTab: (options) => calls.switchTab.push(options),
    },
    (callback) => callback(),
  );

  page.reject.call({ data: { requestId: '' } });

  assert.deepEqual(calls.switchTab, [{ url: '/pages/home/home' }]);
});
