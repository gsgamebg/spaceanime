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
async function api(path, method = 'GET') {
  const response = await fetch(`${API_URL}${path}`, { method, headers: { Authorization: `Bearer ${session.access_token}` } });
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
/** Extracts the SVG markup from a Supabase QR value (data URI, URL-encoded, base64 or raw SVG). */
function svgFrom(value) {
  let text = value.trim();
  if (text.startsWith('data:')) {
    const comma = text.indexOf(',');
    if (comma < 0) return null;
    const head = text.slice(0, comma);
    text = text.slice(comma + 1);
    try { text = head.includes('base64') ? atob(text) : decodeURIComponent(text); } catch { /* keep as is */ }
  }
  text = text.trim();
  return text.startsWith('<svg') || text.startsWith('<?xml') ? text : null;
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
  const svgText = svgFrom(raw);
  const qrSource = svgText ? `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svgText)}` : null;
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
  { id: 'overview', label: 'Visão geral', needs: 'overview.read' },
  { id: 'finance', label: 'Financeiro', needs: 'finance.read' },
  { id: 'ads', label: 'Anúncios', needs: 'ads.read' },
  { id: 'audit', label: 'Auditoria', needs: 'staff.audit.read' },
];
const VIEWS = { overview: renderOverview, finance: renderFinance, ads: renderAds, audit: renderAudit };
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
    if (VIEWS[id]) VIEWS[id](content, me); else renderHome(content, me);
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
      el('p', {}, `${me.permissions.length} permissões ativas. Use o menu acima. Versões, aparelhos e fontes chegam na próxima etapa.`)));
}
const brl = cents => (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const num = value => (value ?? 0).toLocaleString('pt-BR');
const PLAN = { ouro: 'Ouro', platina: 'Platina', ametista: 'Ametista' };
const STATUS = { paid: 'Pago', pending: 'Aguardando', created: 'Criado', expired: 'Expirado', failed: 'Recusado' };
const when = iso => (iso ? new Date(iso).toLocaleString('pt-BR') : '—');
const tile = (label, value, hint) => el('div', { class: 'tile' }, el('span', { class: 'tile-value' }, value), el('span', { class: 'tile-label' }, label), hint ? el('span', { class: 'tile-hint' }, hint) : null);
const loading = content => content.replaceChildren(el('div', { class: 'card' }, el('p', {}, 'Carregando…')));
const failed = (content, e) => { if (e.message !== 'unauthorized') content.replaceChildren(el('div', { class: 'card' }, el('p', { class: 'error' }, 'Não foi possível carregar. Tente de novo em instantes.'))); };

async function renderOverview(content) {
  loading(content);
  try {
    const d = await api('/api/admin/overview');
    const cards = [
      el('div', { class: 'card' }, el('h2', {}, 'Usuários'), el('div', { class: 'tiles' },
        tile('Contas criadas', num(d.users.total)), tile('Novas em 7 dias', num(d.users.new_7d)), tile('Novas em 30 dias', num(d.users.new_30d)))),
      el('div', { class: 'card' }, el('h2', {}, 'Instalações ativas'), el('div', { class: 'tiles' },
        tile('Últimas 24 h', num(d.active.d1)), tile('Últimos 7 dias', num(d.active.d7)), tile('Últimos 30 dias', num(d.active.d30))),
        el('p', {}, 'Conta aparelhos que abriram o app com o envio de dados de uso ligado.')),
    ];
    if (d.revenue) {
      const plans = Object.entries(d.vip.by_plan || {}).map(([p, n]) => `${PLAN[p] || p}: ${n}`).join(' · ') || 'nenhum';
      cards.push(el('div', { class: 'card' }, el('h2', {}, 'Receita (Pix)'), el('div', { class: 'tiles' },
        tile('Neste mês', brl(d.revenue.month_cents), `${num(d.revenue.paid_orders_month)} pagamentos`),
        tile('Últimos 30 dias', brl(d.revenue.last30_cents)), tile('Total', brl(d.revenue.total_cents))),
        el('div', { class: 'tiles' }, tile('VIP ativos', num(d.vip.active_total), plans),
          tile('Pix em aberto', num(d.orders.open), 'aguardando pagamento'), tile('Pix abandonados (30 d)', num(d.orders.abandoned_30d)))));
    }
    content.replaceChildren(...cards, el('p', {}, `Atualizado em ${when(d.generated_at)}.`));
  } catch (e) { failed(content, e); }
}

async function renderFinance(content, me) {
  loading(content);
  try {
    const d = await api('/api/admin/finance');
    const canRecheck = me.permissions.includes('vip.grant');
    const status = Object.entries(d.by_status_30d).map(([s, n]) => `${STATUS[s] || s}: ${n}`).join(' · ') || 'sem pedidos';
    const rows = d.orders.map(order => {
      const stale = (order.status === 'pending' || order.status === 'created') && Date.now() - new Date(order.created_at).getTime() > 40 * 60 * 1000;
      const result = el('span', {});
      const button = canRecheck && order.mp_payment_id && order.status !== 'paid'
        ? el('button', { type: 'button', class: 'ghost small', onclick: async event => {
            event.target.disabled = true;
            try { const r = await api(`/api/admin/finance/orders/${order.id}/recheck`, 'POST'); result.textContent = ` ${STATUS[r.status] || r.status}`; if (r.status === 'paid') renderFinance(content, me); }
            catch { result.textContent = ' erro'; event.target.disabled = false; }
          } }, 'Conferir no Mercado Pago') : null;
      return el('tr', {}, el('td', {}, when(order.created_at)), el('td', {}, PLAN[order.plan] || order.plan), el('td', {}, brl(order.amount_cents)),
        el('td', {}, STATUS[order.status] || order.status, stale ? ' (expirado)' : ''), el('td', {}, order.email || '—'),
        el('td', {}, order.mp_payment_id ? String(order.mp_payment_id) : '—'), el('td', {}, button, result));
    });
    content.replaceChildren(
      el('div', { class: 'card' }, el('h2', {}, 'Resumo (30 dias)'), el('p', {}, status),
        d.paid_without_membership > 0
          ? el('p', { class: 'error' }, `Atenção: ${d.paid_without_membership} pedido(s) pago(s) sem VIP liberado. Use "Conferir no Mercado Pago".`)
          : el('p', {}, 'Conciliação: nenhum pagamento sem VIP liberado.')),
      el('div', { class: 'card scroll' }, el('h2', {}, 'Pedidos recentes'),
        rows.length ? el('table', {}, el('thead', {}, el('tr', {}, ['Quando', 'Plano', 'Valor', 'Situação', 'Cliente', 'Pagamento', ''].map(h => el('th', {}, h)))), el('tbody', {}, rows))
                    : el('p', {}, 'Nenhum pedido ainda.')));
  } catch (e) { failed(content, e); }
}

async function renderAds(content) {
  loading(content);
  try {
    const { config: c } = await api('/api/admin/ads');
    const yes = value => (value ? 'Ligado' : 'Desligado');
    const row = (name, value) => el('tr', {}, el('td', {}, name), el('td', {}, value));
    content.replaceChildren(el('div', { class: 'card' }, el('h2', {}, 'Configuração atual dos anúncios'),
      el('table', {}, el('tbody', {},
        row('Anúncios', yes(c.enabled)), row('Provedor', c.provider === 'startio' ? 'Start.io' : 'AdMob'),
        row('Banner na Início', yes(c.home_banner)), row('Banner no Catálogo', yes(c.catalog_banner)),
        row('Intersticial', c.interstitial_enabled ? `A cada ${c.interstitial_every_episodes} episódios` : 'Desligado'),
        row('Vídeo premiado', c.rewarded_enabled ? `Passe de ${c.rewarded_pass_minutes} min` : 'Desligado'),
        row('Última alteração', when(c.updated_at)))),
      el('p', {}, 'Somente leitura por enquanto. O resultado de cada anúncio (carregou, falhou, exibiu) entra na etapa 2, com o app novo.')));
  } catch (e) { failed(content, e); }
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
