// Applies the saved (or system) color mode before the page paints. Loaded synchronously in <head>.
(function () {
    var mode = null;
    try { mode = localStorage.getItem('hi-theme'); } catch (e) { }
    if (!mode || mode === 'auto') mode = matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    document.documentElement.setAttribute('data-bs-theme', mode);
})();
