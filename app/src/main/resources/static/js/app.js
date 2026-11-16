// Common UI behaviour: theme selector and confirmation of destructive forms
(function () {
    var root = document.documentElement;

    function apply(mode) {
        var effective = mode === 'auto' || !mode
            ? (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light')
            : mode;
        root.setAttribute('data-bs-theme', effective);
        document.querySelectorAll('[data-theme-value]').forEach(function (button) {
            button.classList.toggle('active', button.dataset.themeValue === (mode || 'auto'));
        });
    }

    var saved = null;
    try { saved = localStorage.getItem('hi-theme'); } catch (e) { }
    apply(saved);

    document.addEventListener('click', function (event) {
        var button = event.target.closest('[data-theme-value]');
        if (!button) return;
        var mode = button.dataset.themeValue;
        try { localStorage.setItem('hi-theme', mode); } catch (e) { }
        apply(mode);
    });

    // <form data-confirm="message">
    document.addEventListener('submit', function (event) {
        var message = event.target.dataset.confirm;
        if (message && !confirm(message)) event.preventDefault();
    });
})();

// Copy-to-clipboard buttons: data-copy="#element-id"
document.addEventListener('click', function (event) {
    var button = event.target.closest('[data-copy]');
    if (!button) return;
    var source = document.querySelector(button.dataset.copy);
    var text = 'value' in source ? source.value : source.innerText;
    navigator.clipboard.writeText(text.trim()).then(function () {
        var original = button.innerHTML;
        button.innerHTML = '<i class="bi bi-check2"></i>';
        setTimeout(function () { button.innerHTML = original; }, 1200);
    });
});
