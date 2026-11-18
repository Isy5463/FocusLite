package com.focuslite.app.ui.webview
object WebFilterScripts {
    val INSTAGRAM_CLEAN_JS = """
        (function() {
            if (window.__focusLiteIgInjected) return;
            window.__focusLiteIgInjected = true;
            const style = document.createElement('style');
            style.id = 'focus-lite-ig-style';
            style.innerHTML = `
                a[href*="/reels/"],
                a[href="/explore/"],
                a[href*="/explore/"] {
                    display: none !important;
                    pointer-events: none !important;
                }
            `;
            document.head.appendChild(style);
            function sanitizeInstagramDom() {
                const path = window.location.pathname;
                if (path.startsWith('/reels') || path.startsWith('/explore')) {
                    window.location.replace('/');
                    return;
                }
                const spans = document.querySelectorAll('span, h2, div');
                for (let i = 0; i < spans.length; i++) {
                    const txt = spans[i].innerText;
                    if (txt === 'Vorgeschlagene Beiträge' || txt === 'Suggested Posts' || txt === 'Für dich vorgeschlagen') {
                        const article = spans[i].closest('article');
                        if (article) {
                            article.style.setProperty('display', 'none', 'important');
                        }
                    }
                }
            }
            sanitizeInstagramDom();
            const observer = new MutationObserver(() => { sanitizeInstagramDom(); });
            observer.observe(document.documentElement, { childList: true, subtree: true });
        })();
    """.trimIndent()

    val YOUTUBE_CLEAN_JS = """
        (function() {
            if (window.__focusLiteYtInjected) return;
            window.__focusLiteYtInjected = true;
            const style = document.createElement('style');
            style.id = 'focus-lite-yt-style';
            style.innerHTML = `
                ytm-reel-shelf-renderer,
                ytm-shorts-lockup-view-model,
                ytm-shorts-lockup-view-model-v2,
                ytd-reel-shelf-renderer,
                ytd-rich-shelf-renderer[is-shorts],
                a[href^="/shorts/"],
                ytm-pivot-bar-item-renderer:has(div.pivot-shorts) {
                    display: none !important;
                }
            `;
            document.head.appendChild(style);
            function sanitizeYouTubeDom() {
                if (window.location.pathname.startsWith('/shorts')) {
                    window.location.replace('/feed/subscriptions');
                    return;
                }
                const pivotItems = document.querySelectorAll('ytm-pivot-bar-item-renderer');
                pivotItems.forEach(item => {
                    const text = (item.innerText || '').trim().toLowerCase();
                    if (text === 'shorts') {
                        item.style.setProperty('display', 'none', 'important');
                    }
                });
            }
            sanitizeYouTubeDom();
            const observer = new MutationObserver(() => { sanitizeYouTubeDom(); });
            observer.observe(document.documentElement, { childList: true, subtree: true });
        })();
    """.trimIndent()
}
