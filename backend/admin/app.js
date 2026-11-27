import { SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, API_URL, IDLE_LIMIT_MS } from './config.js';

// Session lives in sessionStorage: it ends when the tab closes. Everything shown is rendered with
// textContent (never innerHTML) so data from the server cannot inject markup.
const root = document.getElementById('app');
const KEY = 'spaceanime-admin-session';
let session = readSession();
let idleTimer = null;

function readSession() {
  try { return JSON.parse(sessionStorage.getItem(KEY) || 'null'); } catch { return null; }
}
function saveSession(value) {
  session = value;
  if (value) sessionStorage.setItem(KEY, JSON.stringify(value)); else sessionStorage.removeItem(KEY);
}

function el(tag, props = {}, ...children) {
  const node = document.createElement(tag);
  for (const [name, value] of Object.entries(props)) {
    if (name === 'class') node.className = value;
    else if (name.startsWith('on')) node.addEventListener(name.slice(2), value);
    else if (value === true) node.setAttribute(name, '');
    else if (value !== false && value != null) node.setAttribute(name, value);
  }
  for (const child of children.flat()) if (child != null && child !== false) node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  return node;
}
function show(...nodes) { root.replaceChildren(...nodes); }

async function auth(path, { method = 'POST', body, token } = {}) {
  const response = await fetch(`${SUPABASE_URL}/auth/v1/${path}`, {
    method,
    headers: { apikey: SUPABASE_PUBLISHABLE_KEY, 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.msg || data.message || data.error_description || `Erro ${response.status}`);
    error.code = data.error_code || data.code || '';
    error.status = response.status;
    throw error;
  }
  return data;
}
/** Plain-language message for an auth failure, keeping the real reason visible for diagnosis. */
function explain(error, step) {
  if (error instanceof TypeError) return 'Sem conexão com o servidor de login. Verifique a internet.';
  if (error.code === 'invalid_credentials' || error.code === 'invalid_login_credentials') return 'E-mail ou senha incorretos.';
  if (error.code === 'email_not_confirmed') return 'Confirme o seu e-mail antes de entrar (veja a caixa de entrada).';
  if (error.status === 429) return 'Muitas tentativas. Aguarde alguns minutos.';
  return `${step}: ${error.message}`;
}
async function api(path) {
  const response = await fetch(`${API_URL}${path}`, { headers: { Authorization: `Bearer ${session.access_token}` } });
  const data = await response.json().catch(() => ({}));
  if (response.status === 401) { signOut('Sua sessão expirou. Entre novamente.'); throw new Error('unauthorized'); }
  if (!response.ok) { const error = new Error(data.error || 'erro'); error.status = response.status; throw error; }
  return data;
}

function signOut(message) {
  if (session?.access_token) auth('logout', { token: session.access_token }).catch(() => {});
  saveSession(null);
  clearTimeout(idleTimer);
  renderLogin(message);
}
function resetIdle() {
  clearTimeout(idleTimer);
  if (session) idleTimer = setTimeout(() => signOut('Você foi desconectado por inatividade.'), IDLE_LIMIT_MS);
}
for (const type of ['click', 'keydown']) document.addEventListener(type, resetIdle);

// ---------------------------------------------------------------- login (password)
function renderLogin(message) {
  const email = el('input', { id: 'email', type: 'email', autocomplete: 'username', required: true });
  const password = el('input', { id: 'password', type: 'password', autocomplete: 'current-password', required: true });
  const error = el('p', { class: 'error', role: 'alert' }, message || '');
  const submit = el('button', { type: 'submit' }, 'Entrar');
  const form = el('form', { class: 'card login' },
    el('h1', {}, 'SpaceAnime · Painel'),
    el('p', {}, 'Acesso restrito à equipe. O login exige verificação em duas etapas.'),
    el('label', { for: 'email' }, 'E-mail'), email,
    el('label', { for: 'password' }, 'Senha'), password,
    submit, error);
  form.addEventListener('submit', async event => {
    event.preventDefault();
    submit.disabled = true; error.textContent = '';
    try {
      let data;
      try { data = await auth('token?grant_type=password', { body: { email: email.value.trim(), password: password.value } }); }
      catch (e) { error.textContent = explain(e, 'Falha no login'); return; }
      password.value = '';
      saveSession({ access_token: data.access_token, refresh_token: data.refresh_token, user: { id: data.user.id, email: data.user.email }, factors: data.user.factors || [] });
      try { await afterPassword(); }
      catch (e) {
        // The password was accepted; the failure is in the second-factor step, so say so.
        saveSession(null);
        error.textContent = `Senha aceita, mas a verificação em duas etapas falhou. ${explain(e, 'Detalhe')}`;
      }
    } finally { submit.disabled = false; }
  });
  show(form);
  email.focus();
}

// ---------------------------------------------------------------- second factor (TOTP)
async function afterPassword() {
  const user = await auth('user', { method: 'GET', token: session.access_token });
  const factor = (user.factors || []).find(f => f.factor_type === 'totp' && f.status === 'verified');
  if (factor) return renderCode(factor.id);
  return renderEnroll();
}
async function verify(factorId, code) {
  const challenge = await auth(`factors/${factorId}/challenge`, { token: session.access_token });
  const data = await auth(`factors/${factorId}/verify`, { token: session.access_token, body: { challenge_id: challenge.id, code } });
  saveSession({ ...session, access_token: data.access_token, refresh_token: data.refresh_token });
  return renderPanel();
}
function renderCode(factorId, message) {
  const code = el('input', { id: 'code', inputmode: 'numeric', autocomplete: 'one-time-code', pattern: '[0-9]{6}', maxlength: '6', required: true });
  const error = el('p', { class: 'error', role: 'alert' }, message || '');
  const form = el('form', { class: 'card login' },
    el('h1', {}, 'Verificação em duas etapas'),
    el('p', {}, 'Digite o código de 6 dígitos do seu app autenticador.'),
    el('label', { for: 'code' }, 'Código'), code, el('button', { type: 'submit' }, 'Confirmar'), error,
    el('button', { type: 'button', class: 'ghost', onclick: () => signOut() }, 'Cancelar'));
  form.addEventListener('submit', async event => {
    event.preventDefault(); error.textContent = '';
    try { await verify(factorId, code.value.trim()); } catch { error.textContent = 'Código incorreto ou expirado.'; code.value = ''; }
  });
  show(form);
  code.focus();
}
async function renderEnroll() {
  const factor = await auth('factors', { token: session.access_token, body: { factor_type: 'totp', friendly_name: `painel-${Date.now()}` } });
  const code = el('input', { id: 'code', inputmode: 'numeric', autocomplete: 'one-time-code', pattern: '[0-9]{6}', maxlength: '6', required: true });
  const error = el('p', { class: 'error', role: 'alert' });
  // Supabase returns the QR either as a data URI or as raw SVG; either way it is only used as an <img> source.
  const raw = String(factor.totp.qr_code || '');
  const qrSource = raw.startsWith('data:image/svg+xml') ? raw
    : raw.trimStart().startsWith('<svg') ? `data:image/svg+xml;utf-8,${encodeURIComponent(raw)}` : null;
  const qr = qrSource ? el('div', { class: 'qr' }, el('img', { src: qrSource, alt: 'QR Code para o app autenticador' })) : el('p', {}, 'Não foi possível mostrar o QR Code. Use o código abaixo no app autenticador.');
  const form = el('form', { class: 'card login' },
    el('h1', {}, 'Ativar verificação em duas etapas'),
    el('p', {}, 'Escaneie o QR Code com um app autenticador (Google Authenticator, Authy, 1Password) e digite o código.'),
    qr, el('p', {}, 'Ou digite este código no app: ', el('code', {}, factor.totp.secret)),
    el('label', { for: 'code' }, 'Código de 6 dígitos'), code, el('button', { type: 'submit' }, 'Ativar e entrar'), error);
  form.addEventListener('submit', async event => {
    event.preventDefault(); error.textContent = '';
    try { await verify(factor.id, code.value.trim()); } catch { error.textContent = 'Código incorreto. Confira a hora do celular e tente de novo.'; code.value = ''; }
  });
  show(form);
  code.focus();
}

// ---------------------------------------------------------------- panel shell
const SECTIONS = [
  { id: 'home', label: 'Início', needs: null },
  { id: 'audit', label: 'Auditoria', needs: 'staff.audit.read' },
];
async function renderPanel() {
  let me;
  try { me = await api('/api/admin/me'); }
  catch (e) {
    if (e.message === 'unauthorized') return;
    const message = e.status === 403 && e.message === 'forbidden' ? 'Esta conta não tem permissão para usar o painel.'
      : e.status === 403 ? 'A verificação em duas etapas é obrigatória.' : 'O painel está indisponível. Tente de novo em instantes.';
    return signOut(message);
  }
  resetIdle();
  const content = el('section', {});
  const nav = el('nav', { 'aria-label': 'Seções' });
  const open = id => {
    for (const button of nav.children) if (button.dataset.id === id) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current');
    if (id === 'audit') renderAudit(content); else renderHome(content, me);
  };
  for (const section of SECTIONS.filter(s => !s.needs || me.permissions.includes(s.needs))) {
    nav.append(el('button', { type: 'button', 'data-id': section.id, onclick: () => open(section.id) }, section.label));
  }
  show(
    el('div', { class: 'bar' },
      el('div', {}, el('h1', {}, 'SpaceAnime · Painel'), el('p', {}, session.user.email)),
      el('button', { type: 'button', class: 'ghost', onclick: () => signOut() }, 'Sair')),
    nav, content);
  open('home');
}
function renderHome(content, me) {
  content.replaceChildren(
    el('div', { class: 'card' },
      el('h2', {}, 'Seu acesso'),
      el('div', { class: 'chips' }, me.roles.map(role => el('span', { class: 'chip' }, role))),
      el('p', {}, `${me.permissions.length} permissões ativas. As telas de receita, anúncios, versões e fontes chegam nas próximas etapas.`)));
}
async function renderAudit(content) {
  content.replaceChildren(el('div', { class: 'card' }, el('p', {}, 'Carregando…')));
  try {
    const data = await api('/api/admin/audit');
    const rows = data.items.map(item => el('tr', {},
      el('td', {}, new Date(item.created_at).toLocaleString('pt-BR')),
      el('td', {}, item.action), el('td', {}, `${item.target_type} ${item.target_id}`), el('td', {}, item.actor_id || '')));
    content.replaceChildren(el('div', { class: 'card' }, el('h2', {}, 'Auditoria'),
      rows.length ? el('table', {}, el('thead', {}, el('tr', {}, ['Quando', 'Ação', 'Alvo', 'Quem'].map(h => el('th', {}, h)))), el('tbody', {}, rows))
                  : el('p', {}, 'Nenhuma alteração registrada ainda.')));
  } catch (e) {
    if (e.message !== 'unauthorized') content.replaceChildren(el('div', { class: 'card' }, el('p', { class: 'error' }, 'Não foi possível carregar a auditoria.')));
  }
}

// ---------------------------------------------------------------- start
if (session?.access_token) renderPanel(); else renderLogin();
