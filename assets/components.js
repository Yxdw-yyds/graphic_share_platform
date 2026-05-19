/* αcFun · 公共 JS 组件
 * 引入: <script src="assets/components.js"></script>
 * 提供 toast、modal、formatTime、captcha、skeleton、empty、pager 等可复用工具。
 */
(function (global) {
  // —— Toast ——
  function ensureToastContainer() {
    let c = document.getElementById('acf-toast-container');
    if (!c) {
      c = document.createElement('div');
      c.id = 'acf-toast-container';
      c.className = 'acf-toast-container';
      document.body.appendChild(c);
    }
    return c;
  }

  function toast(msg, type) {
    type = type || 'info';
    const c = ensureToastContainer();
    const el = document.createElement('div');
    el.className = 'acf-toast acf-toast-' + type;
    const icon = { info: '💬', success: '✅', error: '⚠️', warning: '⏳' }[type] || '💬';
    el.innerHTML = '<span class="acf-toast-icon">' + icon + '</span><span>' + escapeHtml(msg) + '</span>';
    c.appendChild(el);
    setTimeout(() => {
      el.classList.add('acf-out');
      setTimeout(() => el.remove(), 200);
    }, 2600);
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, m => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
  }

  // —— Time formatting ——
  function formatTime(input) {
    if (!input) return '';
    const d = (input instanceof Date) ? input : new Date(input);
    if (isNaN(d.getTime())) return String(input);
    const diff = (Date.now() - d.getTime()) / 1000;
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前';
    if (diff < 86400 * 2) return '昨天';
    if (diff < 86400 * 7) return Math.floor(diff / 86400) + ' 天前';
    return d.toLocaleDateString();
  }

  // —— Modal helpers ——
  function openModal(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'flex';
  }
  function closeModal(id) {
    const el = document.getElementById(id);
    if (el) el.style.display = 'none';
  }

  // —— CAPTCHA ——
  function generateCaptcha(len) {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let s = '';
    for (let i = 0; i < (len || 4); i++) s += chars.charAt(Math.floor(Math.random() * chars.length));
    return s;
  }

  // —— Skeleton ——
  function skeleton(count) {
    let html = '';
    for (let i = 0; i < (count || 3); i++) {
      html += '<div class="acf-skeleton acf-skeleton-card"></div>';
    }
    return html;
  }

  // —— Empty state ——
  function emptyState(opts) {
    opts = opts || {};
    const icon = opts.icon || '📭';
    const text = opts.text || '暂无数据';
    const action = opts.action;
    let btn = '';
    if (action && action.label && action.onclick) {
      const tmp = '__acf_empty_cb_' + Date.now();
      global[tmp] = action.onclick;
      btn = '<button class="acf-btn" onclick="' + tmp + '()">' + escapeHtml(action.label) + '</button>';
    }
    return '<div class="acf-empty"><div class="acf-empty-icon">' + icon + '</div>' +
      '<div class="acf-empty-text">' + escapeHtml(text) + '</div>' + btn + '</div>';
  }

  // —— Pager ——
  function renderPager(opts) {
    const page = opts.page || 1;
    const pages = opts.pages || 1;
    if (pages <= 1) return '';
    const cb = '__acf_pager_cb_' + Date.now();
    global[cb] = opts.onChange;
    let html = '<div class="acf-pager">';
    html += '<button ' + (page <= 1 ? 'disabled' : '') + ' onclick="' + cb + '(' + (page - 1) + ')">‹</button>';
    const win = 5;
    const start = Math.max(1, page - Math.floor(win / 2));
    const end = Math.min(pages, start + win - 1);
    for (let i = start; i <= end; i++) {
      html += '<button class="' + (i === page ? 'active' : '') + '" onclick="' + cb + '(' + i + ')">' + i + '</button>';
    }
    html += '<button ' + (page >= pages ? 'disabled' : '') + ' onclick="' + cb + '(' + (page + 1) + ')">›</button>';
    html += '</div>';
    return html;
  }

  // —— Confirm (Promise-based) ——
  function confirmDialog(message) {
    return new Promise(resolve => {
      const ok = global.confirm(message);
      resolve(!!ok);
    });
  }

  global.acf = {
    toast, formatTime, openModal, closeModal,
    generateCaptcha, skeleton, emptyState, renderPager,
    confirm: confirmDialog, escapeHtml,
  };
})(window);
