// Photos of the report elements: upload (camera, gallery, drag & drop), caption, order, cover, delete.
(function () {
    'use strict';
    var messagesEl = document.getElementById('editorMessages');
    var messages = messagesEl ? JSON.parse(messagesEl.textContent || '{}') : {};
    var csrfToken = document.querySelector('meta[name="_csrf"]');
    var csrfHeader = document.querySelector('meta[name="_csrf_header"]');

    function headers(json) {
        var h = { 'Accept': 'application/json' };
        if (json) h['Content-Type'] = 'application/json';
        if (csrfToken && csrfHeader) h[csrfHeader.content] = csrfToken.content;
        return h;
    }

    function format(template, a, b) {
        return String(template || '').replace('{0}', a).replace('{1}', b);
    }

    function request(method, url, body) {
        return fetch(url, {
            method: method, headers: headers(body !== undefined), credentials: 'same-origin', redirect: 'manual',
            body: body === undefined ? undefined : JSON.stringify(body)
        }).then(function (response) {
            if (!response.ok) throw new Error('HTTP ' + response.status);
            return response.status === 204 ? null : response.json();
        });
    }

    function container(el) { return el.closest('.item-photos'); }
    function photoUrl(box, id, suffix) { return box.dataset.photoBase + id + (suffix || ''); }

    function updateCount(box) {
        var count = box.querySelectorAll('.photo-card').length;
        var badge = box.querySelector('.photo-count');
        if (badge) badge.textContent = count;
    }

    function status(box, text, isError) {
        var el = box.querySelector('.upload-status');
        if (!el) return;
        el.textContent = text || '';
        el.classList.toggle('text-danger', !!isError);
    }

    // ---- card rendering (same markup as the Thymeleaf fragment) ----
    function card(photo, editable) {
        var figure = document.createElement('figure');
        figure.className = 'photo-card';
        figure.dataset.photoId = photo.id;

        var link = document.createElement('a');
        link.className = 'photo-thumb';
        link.href = photo.url;
        link.target = '_blank';
        link.rel = 'noopener';
        var img = document.createElement('img');
        img.src = photo.thumbnailUrl;
        img.alt = photo.caption || '';
        img.loading = 'lazy';
        link.appendChild(img);
        var coverBadge = document.createElement('span');
        coverBadge.className = 'badge text-bg-warning photo-cover-badge' + (photo.cover ? '' : ' d-none');
        coverBadge.innerHTML = '<i class="bi bi-star-fill me-1"></i>';
        coverBadge.appendChild(document.createTextNode(messages.cover || ''));
        link.appendChild(coverBadge);
        figure.appendChild(link);

        var caption = document.createElement('figcaption');
        var input = document.createElement('input');
        input.type = 'text';
        input.className = 'form-control form-control-sm';
        input.maxLength = 500;
        input.dataset.caption = '';
        input.placeholder = messages.caption || '';
        input.setAttribute('aria-label', messages.caption || '');
        input.value = photo.caption || '';
        caption.appendChild(input);
        if (editable) {
            var group = document.createElement('div');
            group.className = 'btn-group btn-group-sm w-100 mt-1';
            [['left', 'secondary', 'bi-arrow-left', messages.moveLeft],
             ['right', 'secondary', 'bi-arrow-right', messages.moveRight],
             ['cover', 'warning', 'bi-star', messages.makeCover],
             ['delete', 'danger', 'bi-trash', messages.remove]].forEach(function (b) {
                var button = document.createElement('button');
                button.type = 'button';
                button.className = 'btn btn-outline-' + b[1];
                button.dataset.photoAction = b[0];
                button.title = b[3] || '';
                button.innerHTML = '<i class="bi ' + b[2] + '"></i>';
                group.appendChild(button);
            });
            caption.appendChild(group);
        }
        figure.appendChild(caption);
        return figure;
    }

    // ---- upload: one file per request, so progress is visible and memory stays low ----
    function upload(box, files) {
        files = Array.prototype.slice.call(files || []);
        if (!files.length) return;
        var grid = box.querySelector('.photo-grid');
        var errors = [];
        var index = 0;

        function next() {
            if (index >= files.length) {
                status(box, errors.length ? errors.join(' · ') : messages.uploaded, errors.length > 0);
                updateCount(box);
                return;
            }
            var file = files[index++];
            status(box, format(messages.uploading, index, files.length));
            var data = new FormData();
            data.append('files', file, file.name);
            fetch(box.dataset.uploadUrl, {
                method: 'POST', headers: headers(false), body: data, credentials: 'same-origin', redirect: 'manual'
            }).then(function (response) {
                if (response.type === 'opaqueredirect') throw new Error('session');
                return response.json().catch(function () { return null; }).then(function (result) {
                    if (!result) throw new Error('HTTP ' + response.status);
                    (result.photos || []).forEach(function (photo) { grid.appendChild(card(photo, true)); });
                    (result.errors || []).forEach(function (e) { errors.push((e.file ? e.file + ': ' : '') + e.message); });
                });
            }).catch(function () {
                errors.push(file.name + ': ' + (messages.uploadFailed || 'Error'));
            }).then(next);
        }
        next();
    }

    document.addEventListener('change', function (event) {
        var input = event.target.closest('[data-photo-input]');
        if (input) {
            upload(container(input), input.files);
            input.value = '';
            return;
        }
        var caption = event.target.closest('[data-caption]');
        if (caption) {
            var box = container(caption);
            var id = caption.closest('.photo-card').dataset.photoId;
            request('PUT', photoUrl(box, id), { caption: caption.value })
                .then(function () { caption.classList.remove('is-invalid'); caption.classList.add('is-valid'); })
                .catch(function () { caption.classList.add('is-invalid'); });
        }
    });

    document.addEventListener('click', function (event) {
        var button = event.target.closest('[data-photo-action]');
        if (!button) return;
        var box = container(button);
        var cardEl = button.closest('.photo-card');
        var id = cardEl.dataset.photoId;
        var action = button.dataset.photoAction;

        if (action === 'delete') {
            if (!confirm(messages.deleteConfirm)) return;
            request('DELETE', photoUrl(box, id)).then(function () {
                cardEl.remove();
                updateCount(box);
            }).catch(function () { status(box, messages.uploadFailed, true); });
        } else if (action === 'cover') {
            request('POST', photoUrl(box, id, '/cover')).then(function (result) {
                // Only one cover photo per report
                document.querySelectorAll('.photo-cover-badge').forEach(function (b) { b.classList.add('d-none'); });
                if (result && result.cover) cardEl.querySelector('.photo-cover-badge').classList.remove('d-none');
            }).catch(function () { status(box, messages.uploadFailed, true); });
        } else if (action === 'left' || action === 'right') {
            var sibling = action === 'left' ? cardEl.previousElementSibling : cardEl.nextElementSibling;
            if (!sibling) return;
            if (action === 'left') sibling.before(cardEl); else sibling.after(cardEl);
            var ids = Array.prototype.map.call(box.querySelectorAll('.photo-card'), function (c) {
                return Number(c.dataset.photoId);
            });
            request('PUT', box.dataset.orderUrl, { ids: ids })
                .catch(function () { status(box, messages.uploadFailed, true); });
        }
    });

    // ---- drag & drop (desktop) ----
    document.querySelectorAll('.photo-dropzone').forEach(function (zone) {
        var box = container(zone);
        ['dragenter', 'dragover'].forEach(function (type) {
            box.addEventListener(type, function (event) {
                if (!event.dataTransfer || Array.prototype.indexOf.call(event.dataTransfer.types, 'Files') < 0) return;
                event.preventDefault();
                box.classList.add('is-dragover');
            });
        });
        ['dragleave', 'drop'].forEach(function (type) {
            box.addEventListener(type, function () { box.classList.remove('is-dragover'); });
        });
        box.addEventListener('drop', function (event) {
            if (!event.dataTransfer || !event.dataTransfer.files.length) return;
            event.preventDefault();
            upload(box, event.dataTransfer.files);
        });
    });
})();
