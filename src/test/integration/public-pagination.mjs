import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';

// Only run against a fresh disposable Halo instance; setup creates a temporary administrator.
const [base, oldJar, newJar, themeZip, reportFile] = process.argv.slice(2);
assert(base && oldJar && newJar && themeZip && reportFile, 'Expected base URL, old JAR, new JAR, theme ZIP, report path');
const admin = '/apis/api.console.halo.run/v1alpha1';
const resources = '/apis/wishboard.aobp.cn/v1alpha1/wishes';
const publicApi = '/apis/anonymous.wishboard.aobp.cn/v1alpha1/wishes';
const password = randomBytes(24).toString('hex');
const username = 'wishboard-pagination-admin';
const auth = 'Basic ' + Buffer.from(`${username}:${password}`).toString('base64');
const statuses = ['approved', 'pending', 'doing', 'done'];
const fixtures = [];
let checks = 0;

async function request(path, { method = 'GET', body, authenticated = true, expected = [200], headers = {} } = {}) {
  const response = await fetch(base + path, {
    method,
    headers: { Accept: 'application/json', ...(authenticated ? { Authorization: auth } : {}), ...headers },
    body,
    redirect: 'manual',
    signal: AbortSignal.timeout(60000),
  });
  assert(expected.includes(response.status), `${method} ${path}: expected ${expected}, received ${response.status}`);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

async function json(path, method, value, expected = [200, 201]) {
  return request(path, { method, body: JSON.stringify(value), headers: { 'Content-Type': 'application/json' }, expected });
}

async function upload(path, file) {
  const form = new FormData();
  form.set('file', new Blob([await readFile(file)]), file.split(/[\\/]/).at(-1));
  return request(path, { method: 'POST', body: form, expected: [200, 201] });
}

async function enablePlugin() {
  await json(`${admin}/plugins/PluginWishboard/plugin-state`, 'PUT', { enable: true, async: false });
}

async function waitForPublicRoute() {
  for (let attempt = 0; attempt < 30; attempt++) {
    const response = await fetch(base + publicApi, { signal: AbortSignal.timeout(10000) });
    await response.arrayBuffer();
    if (response.status === 200) return;
    assert.equal(response.status, 404, 'Plugin loading must not conceal query errors');
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new Error('Public route did not become ready after plugin upgrade');
}

async function create(name, status, type, createdAt, extra = {}) {
  const wish = {
    apiVersion: 'wishboard.aobp.cn/v1alpha1', kind: 'Wish', metadata: { name, ...extra },
    spec: {
      content: `Public content ${name}`, nickname: `Private nickname ${name}`,
      author: 'private-account', ip: '192.0.2.1', color: 'green', type, status,
      anonymous: name.endsWith('0'), createdAt, completedAt: null,
      aiReply: 'A kind reply', emotionTag: 'happy', doneImage: '', doneNote: '', priority: 'normal',
    },
  };
  if (name === 'pagination-001') wish.spec.nickname = '   ';
  const saved = await json(resources, 'POST', wish);
  fixtures.push(saved);
  return saved;
}

function expectedItems({ type, status, sort = 'createdAt,desc' } = {}) {
  return fixtures.filter(w => statuses.includes(w.spec.status) && !w.metadata.deletionTimestamp
    && (!type || w.spec.type === type) && (!status || w.spec.status === status))
    .sort((a, b) => {
      const ta = a.spec.createdAt ? Date.parse(a.spec.createdAt) : -Infinity;
      const tb = b.spec.createdAt ? Date.parse(b.spec.createdAt) : -Infinity;
      const comparison = ta === tb ? 0 : (ta < tb ? -1 : 1);
      return (sort.endsWith('asc') ? comparison : -comparison)
        || a.metadata.name.localeCompare(b.metadata.name);
    });
}

async function verifyPage(params = {}) {
  const page = params.page ?? 1, size = params.size ?? 20;
  const expected = expectedItems(params);
  const result = await request(publicApi + '?' + new URLSearchParams(params), { authenticated: false });
  assert.equal(result.page, page);
  assert.equal(result.size, size);
  assert.equal(result.total, expected.length);
  assert.equal(result.totalPages, Math.ceil(expected.length / size));
  assert.equal(result.hasNext, page < result.totalPages);
  assert.equal(result.hasPrevious, page > 1);
  assert.equal(result.first, page === 1);
  assert.equal(result.last, !result.hasNext);
  assert.deepEqual(result.items.map(w => w.metadata.name),
    expected.slice((page - 1) * size, page * size).map(w => w.metadata.name));
  for (const item of result.items) {
    assert.deepEqual(Object.keys(item).sort(), ['metadata', 'spec']);
    assert.deepEqual(Object.keys(item.metadata), ['name']);
    assert(!('ip' in item.spec));
    assert(!('author' in item.spec));
    const source = fixtures.find(w => w.metadata.name === item.metadata.name);
    assert.equal(item.spec.nickname, source.spec.anonymous || !source.spec.nickname?.trim()
      ? '\u533f\u540d' : source.spec.nickname);
  }
  checks++;
  return result;
}

console.log(`${base}: initializing isolated Halo instance`);
for (let attempt = 0; ; attempt++) {
  try {
    const response = await fetch(base + '/system/setup', {
      redirect: 'manual', signal: AbortSignal.timeout(3000),
    });
    await response.arrayBuffer();
    if (response.status === 200) break;
  } catch {}
  assert(attempt < 60, 'Fresh Halo instance did not become ready for setup');
  await new Promise(resolve => setTimeout(resolve, 1000));
}
await request('/system/setup', {
  method: 'POST', authenticated: false, expected: [204],
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ username, password, email: 'pagination@example.invalid',
    siteTitle: 'Wishboard pagination test', language: 'zh-CN', externalUrl: base }),
});
await upload(`${admin}/plugins/install`, oldJar);
await enablePlugin();
console.log(`${base}: old plugin installed; seeding historical data`);
for (let i = 0; i < 53; i++) {
  const time = i === 52 ? null : new Date(Date.UTC(2026, 0, 1 + Math.floor(i / 2))).toISOString();
  await create(`pagination-${String(i).padStart(3, '0')}`, statuses[i % 4],
    ['wish', 'treehole', 'custom-type'][i % 3], time);
}
for (const [name, status] of [['review', 'pending_review'], ['rejected', 'rejected'],
  ['unknown', 'unknown'], ['missing', null]]) {
  await create(`hidden-${name}`, status, 'wish', '2026-02-01T00:00:00Z');
}
const deleted = await create('hidden-deleted', 'approved', 'wish', '2026-02-01T00:00:00Z',
  { finalizers: ['pagination-test/hold'] });
await request(`${resources}/${deleted.metadata.name}`, { method: 'DELETE', expected: [200, 202] });
const stillStored = await request(`${resources}/${deleted.metadata.name}`);
assert(stillStored.metadata.deletionTimestamp, 'A retained deleted fixture must remain in storage');
Object.assign(deleted, stillStored);

console.log(`${base}: upgrading plugin and verifying indexed historical data`);
await upload(`${admin}/plugins/PluginWishboard/upgrade`, newJar);
await enablePlugin();
await waitForPublicRoute();
await verifyPage();
for (const sort of ['createdAt,asc', 'createdAt,desc']) {
  for (const page of [1, 2, 3, 4]) await verifyPage({ page, size: 20, sort });
  await verifyPage({ page: 1, size: 100, sort });
  for (const type of ['wish', 'treehole', 'custom-type', 'missing-type']) {
    await verifyPage({ type, size: 7, sort });
    for (const status of statuses) await verifyPage({ type, status, size: 3, sort });
  }
}
await verifyPage({ page: 2147483647, size: 1 });
const blank = await request(publicApi + '?type=%20&status=%20&sort=%20', { authenticated: false });
assert.equal(blank.total, 53);
checks++;
for (const query of ['page=0', 'page=-1', 'page=x', 'page=', 'size=0', 'size=101', 'size=',
  'page=2147483647&size=2', 'status=pending_review', 'status=rejected', 'status=unknown',
  'sort=priority,desc']) {
  const error = await request(publicApi + '?' + query, { authenticated: false, expected: [400] });
  assert.equal(typeof error.error, 'string');
  checks++;
}
for (const path of [resources, '/apis/console.api.wishboard.aobp.cn/v1alpha1/wishes']) {
  const response = await fetch(base + path, {
    headers: { Accept: 'application/json' }, redirect: 'manual', signal: AbortSignal.timeout(10000),
  });
  if (response.status === 302) {
    assert.equal(new URL(response.headers.get('location'), base).pathname, '/login');
  } else {
    assert([401, 403].includes(response.status), 'Anonymous access must be denied');
  }
  const body = await response.text();
  assert(!body.includes('private-account') && !body.includes('192.0.2.1'));
  checks++;
}
const raw = await request(`${resources}/pagination-000`);
assert.equal(raw.spec.ip, '192.0.2.1');
assert.equal(raw.spec.author, 'private-account');
assert.equal(raw.spec.nickname, 'Private nickname pagination-000');
checks++;

console.log(`${base}: verifying actual Thymeleaf Finder rendering`);
await upload(`${admin}/themes/install`, themeZip);
await request(`${admin}/themes/theme-pagination-test/activation`, { method: 'PUT' });
let html;
for (let attempt = 0; ; attempt++) {
  const response = await fetch(base + '/', { signal: AbortSignal.timeout(60000) });
  assert.equal(response.status, 200);
  html = await response.text();
  if (html.includes('<title>Pagination Test</title>')) break;
  assert(attempt < 30, 'Test theme did not become active');
  await new Promise(resolve => setTimeout(resolve, 1000));
}
assert(html.includes('data-total="53"'), 'Default Finder must expose total 53');
assert(html.includes('data-page="1"'));
assert(html.includes('data-size="5"'));
const filtered = expectedItems({ type: 'wish', status: 'doing' });
assert(html.includes(`data-filtered-total="${filtered.length}"`));
assert(html.includes('data-legacy-total="56"'), 'Old Finder visibility must remain unchanged');
for (const entry of expectedItems().slice(0, 5)) {
  assert(html.includes(`data-public-name="${entry.metadata.name}"`));
}
for (const entry of filtered.slice(0, 2)) {
  assert(html.includes(`data-filtered-name="${entry.metadata.name}"`));
}
assert(!html.includes('192.0.2.1'));
assert(!html.includes('private-account'));
checks++;

const visible = fixtures.find(w => w.metadata.name === 'pagination-000');
const update = await request(`${resources}/${visible.metadata.name}`);
update.spec.status = 'rejected';
Object.assign(visible, await json(`${resources}/${visible.metadata.name}`, 'PUT', update));
await verifyPage({ size: 100 });
const reviewed = fixtures.find(w => w.metadata.name === 'hidden-review');
const approved = await request(`${resources}/${reviewed.metadata.name}`);
approved.spec.status = 'approved';
Object.assign(reviewed, await json(`${resources}/${reviewed.metadata.name}`, 'PUT', approved));
await verifyPage({ size: 100 });
await verifyPage({ type: 'wish', status: 'approved', sort: 'createdAt,asc', size: 100 });

const report = { base, checks, publicTotal: expectedItems().length, historicalRecords: fixtures.length,
  upgrade: 'passed', finderTemplate: 'passed', privacy: 'passed', anonymousPermissions: 'passed',
  statusChanges: 'passed' };
await writeFile(reportFile, JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify(report));
