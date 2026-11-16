// Report editor: autosave of every element / section on its own (fetch + PUT JSON).
// Unsaved changes are kept in localStorage until the server confirms them, retried when the
// connection comes back, and the browser warns before leaving the page with pending changes.
(function () {
    'use strict';
    var main = document.querySelector('[data-report-id]');
    if (!main) return;
    var reportId = main.dataset.reportId;
    var messages = JSON.parse(document.getElementById('editorMessages').textContent || '{}');
    var csrfToken = document.querySelector('meta[name="_csrf"]');
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    var statusEl = document.getElementById('saveStatus');
    var DEBOUNCE = 800;
    var timers = new Map();
    var dirty = new Set();
    var retryDelay = 5000;
    var retryTimer = null;
    var sessionExpired = false;

    function storageKey(form) {
        return 'hi-autosave:' + reportId + ':' + form.dataset.autosave;
    }

    function setStatus(state, text) {
        if (!statusEl) return;
        var icons = {
            saved: 'bi-cloud-check', saving: 'bi-arrow-repeat', pending: 'bi-pencil',
            offline: 'bi-wifi-off', error: 'bi-exclamation-triangle'
        };
        statusEl.className = 'save-status small is-' + state;
        statusEl.innerHTML = '<i class="bi ' + (icons[state] || 'bi-cloud') + '"></i> ';
        var span = document.createElement('span');
        span.textContent = text;
        statusEl.appendChild(span);
    }

    function serialize(form) {
        var data = new FormData(form);
        if (form.dataset.kind === 'section') {
            return {
                notApplicable: data.get('notApplicable') === 'true',
                inspectionMethod: data.get('inspectionMethod') || null,
                generalComments: data.get('generalComments') || null,
                generalLimitations: data.get('generalLimitations') || null
            };
        }
        return {
            status: data.get('status') || null,
            rating: data.get('rating') || null,
            materials: data.getAll('materials'),
            materialOther: data.get('materialOther') || null,
            limitations: data.getAll('limitations'),
            limitationText: data.get('limitationText') || null,
            observations: data.get('observations') || null,
            recommendation: data.get('recommendation') || null,
            recommendationText: data.get('recommendationText') || null
        };
    }

    // FormData ignores disabled fields: read the rating even when its group is disabled
    function payload(form) {
        var body = serialize(form);
        if (form.dataset.kind === 'item' && !body.rating) {
            var checked = form.querySelector('input[name="rating"]:checked');
            body.rating = checked ? checked.value : null;
        }
        return body;
    }

    function backup(form) {
        try { localStorage.setItem(storageKey(form), JSON.stringify(payload(form))); } catch (e) { }
    }

    function clearBackup(form) {
        try { localStorage.removeItem(storageKey(form)); } catch (e) { }
    }

    function schedule(form, delay) {
        if (sessionExpired) return;
        dirty.add(form);
        backup(form);
        setStatus('pending', messages.pending);
        clearTimeout(timers.get(form));
        timers.set(form, setTimeout(function () { save(form); }, delay === undefined ? DEBOUNCE : delay));
    }

    function save(form) {
        timers.delete(form);
        setStatus('saving', messages.saving);
        var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
        if (csrfToken && csrfHeader) headers[csrfHeader.content] = csrfToken.content;
        fetch(form.dataset.autosave, {
            method: 'PUT', headers: headers, body: JSON.stringify(payload(form)),
            credentials: 'same-origin', redirect: 'manual'
        }).then(function (response) {
            if (response.type === 'opaqueredirect' || response.status === 401 || response.status === 403) {
                sessionExpired = true;
                setStatus('error', messages.expired);
                return;
            }
            if (response.status === 409) {
                dirty.delete(form);
                clearBackup(form);
                setStatus('error', messages.locked);
                return;
            }
            if (!response.ok) throw new Error('HTTP ' + response.status);
            return response.json().then(function (result) {
                dirty.delete(form);
                clearBackup(form);
                retryDelay = 5000;
                updateProgress(form, result);
                if (dirty.size === 0) setStatus('saved', messages.saved);
            });
        }).catch(function () {
            // Network down or server error: keep the change and retry later
            dirty.add(form);
            setStatus('offline', messages.offline);
            scheduleRetry();
        });
    }

    function scheduleRetry() {
        if (retryTimer) return;
        retryTimer = setTimeout(function () {
            retryTimer = null;
            retryDelay = Math.min(retryDelay * 2, 60000);
            dirty.forEach(function (form) { if (!timers.has(form)) save(form); });
        }, retryDelay);
    }

    function updateProgress(form, result) {
        if (form.dataset.kind === 'item') {
            var card = document.getElementById('item-' + form.dataset.itemId);
            var icon = card && card.querySelector('.item-done');
            if (icon) {
                icon.className = 'bi item-done ' + (result.itemCompleted
                    ? 'bi-check-circle-fill text-success' : 'bi-circle text-body-tertiary');
            }
            refreshItemBadges(form, card);
        }
        var nav = document.querySelector('.section-nav .active .section-progress');
        if (nav && !nav.textContent.match(/^\D/)) {
            nav.textContent = result.sectionCompleted + '/' + result.sectionTotal;
            nav.className = 'badge rounded-pill section-progress ' +
                (result.sectionTotal > 0 && result.sectionCompleted === result.sectionTotal ? 'text-bg-success' : 'text-bg-light border');
        }
    }

    // Rating / state badge in the accordion header, from the current form values
    function refreshItemBadges(form, card) {
        if (!card) return;
        var button = card.querySelector('.accordion-button');
        button.querySelectorAll('.rating-badge').forEach(function (badge) { badge.remove(); });
        var status = form.querySelector('input[name="status"]:checked');
        var rating = form.querySelector('input[name="rating"]:checked');
        var badge = document.createElement('span');
        if (status && status.value === 'INSPECTED' && rating) {
            badge.className = 'badge rating-badge rating-' + rating.value;
            badge.textContent = form.querySelector('label[for="' + rating.id + '"]').textContent;
        } else if (status && status.value !== 'INSPECTED') {
            badge.className = 'badge text-bg-light border rating-badge';
            badge.textContent = form.querySelector('label[for="' + status.id + '"]').textContent;
        } else {
            return;
        }
        button.appendChild(badge);
    }

    function toggleRating(form) {
        var group = form.querySelector('.rating-group');
        if (!group) return;
        var status = form.querySelector('input[name="status"]:checked');
        var inspected = status && status.value === 'INSPECTED';
        group.disabled = !inspected || form.querySelector('fieldset').disabled;
        if (!inspected) {
            form.querySelectorAll('input[name="rating"]').forEach(function (input) { input.checked = false; });
        }
    }

    var forms = document.querySelectorAll('form[data-autosave]');
    forms.forEach(function (form) {
        var fields = form.querySelector('fieldset');
        if (fields && fields.disabled) return; // final report: read-only
        form.addEventListener('submit', function (event) { event.preventDefault(); });
        form.addEventListener('change', function (event) {
            if (event.target.name === 'status') toggleRating(form);
            if (event.target.closest('.item-photos')) return;
            schedule(form, event.target.tagName === 'TEXTAREA' || event.target.type === 'text' ? DEBOUNCE : 150);
        });
        form.addEventListener('input', function (event) {
            if (event.target.tagName === 'TEXTAREA' || event.target.type === 'text') schedule(form);
        });

        // Changes left unsaved by a previous visit (connection lost, tab closed...)
        var stored = null;
        try { stored = localStorage.getItem(storageKey(form)); } catch (e) { }
        if (stored) {
            try {
                restore(form, JSON.parse(stored));
                schedule(form, 300);
                showRestored(form);
            } catch (e) { clearBackup(form); }
        }
    });

    function restore(form, values) {
        Object.keys(values).forEach(function (name) {
            var value = values[name];
            var inputs = form.querySelectorAll('[name="' + name + '"]');
            inputs.forEach(function (input) {
                if (input.type === 'checkbox') {
                    input.checked = Array.isArray(value) ? value.indexOf(input.value) >= 0 : value === true;
                } else if (input.type === 'radio') {
                    input.checked = input.value === value;
                } else {
                    input.value = value == null ? '' : value;
                }
            });
        });
        toggleRating(form);
    }

    function showRestored(form) {
        var collapse = form.closest('.accordion-collapse');
        if (collapse && window.bootstrap) bootstrap.Collapse.getOrCreateInstance(collapse, { toggle: false }).show();
        var note = document.createElement('div');
        note.className = 'alert alert-warning py-2 small';
        note.textContent = messages.restored;
        form.prepend(note);
    }

    // Comment library: insert the phrase in the observations
    document.addEventListener('click', function (event) {
        var button = event.target.closest('[data-insert-comment]');
        if (!button) return;
        var textarea = document.getElementById(button.dataset.target);
        if (!textarea || textarea.disabled) return;
        var current = textarea.value.trim();
        textarea.value = current ? current + '\n' + button.dataset.insertComment : button.dataset.insertComment;
        textarea.dispatchEvent(new Event('input', { bubbles: true }));
        textarea.focus();
    });

    // Section select (mobile)
    var select = document.querySelector('[data-navigate]');
    if (select) select.addEventListener('change', function () { window.location.href = select.value; });

    // Open the element from the URL hash (#item-123)
    if (location.hash && location.hash.indexOf('#item-') === 0) {
        var target = document.querySelector(location.hash + ' .accordion-collapse');
        if (target && window.bootstrap) bootstrap.Collapse.getOrCreateInstance(target, { toggle: false }).show();
    }

    window.addEventListener('online', function () { dirty.forEach(function (form) { save(form); }); });
    window.addEventListener('beforeunload', function (event) {
        if (dirty.size > 0 || timers.size > 0) {
            event.preventDefault();
            event.returnValue = '';
        }
    });
    // Leaving the page: flush pending saves right away
    document.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'hidden') {
            timers.forEach(function (timer, form) { clearTimeout(timer); save(form); });
        }
    });
})();
