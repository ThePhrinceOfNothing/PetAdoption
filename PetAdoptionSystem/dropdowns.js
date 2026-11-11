/**
 * Happy Paws - Animated UI Motion & Custom Select Dropdowns
 * Built in strict accordance with the 20 Golden Rules of UI Motion:
 * - Rule 2: Dropdowns open in 180ms (cubic-bezier(0.16, 1, 0.3, 1))
 * - Rule 4: Scale down to 0.97 on press
 * - Rule 8: Subtle pulse confirmation on action click
 * - Rule 10: Start popovers/dropdowns at scale(0.96) translateY(-4px)
 * - Rule 11 & 12: Animate origin directly from the trigger
 * - Rule 13: Zero bounce on daily utility controls
 * - Rule 15: Make exits ~20% faster (140ms)
 */

(function() {
    'use strict';

    function initCustomSelect(selectEl) {
        if (!selectEl || selectEl.dataset.customEnhanced === 'true') return;
        selectEl.dataset.customEnhanced = 'true';

        // Hide original native select visually but keep in DOM for form validation & value
        selectEl.classList.add('original-select-hidden');

        // Create container wrapper
        const wrapper = document.createElement('div');
        wrapper.className = 'custom-select-wrapper';
        if (selectEl.id) {
            wrapper.dataset.forId = selectEl.id;
        }

        // Create trigger button
        const trigger = document.createElement('button');
        trigger.type = 'button';
        trigger.className = 'custom-select-trigger';
        trigger.setAttribute('aria-haspopup', 'listbox');
        trigger.setAttribute('aria-expanded', 'false');

        const labelSpan = document.createElement('span');
        labelSpan.className = 'custom-select-label';

        const arrowSpan = document.createElement('span');
        arrowSpan.className = 'custom-select-arrow';
        arrowSpan.innerHTML = `
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="6 9 12 15 18 9"></polyline>
            </svg>
        `;

        trigger.appendChild(labelSpan);
        trigger.appendChild(arrowSpan);
        wrapper.appendChild(trigger);

        // Create dropdown menu list
        const menu = document.createElement('div');
        menu.className = 'custom-select-menu';
        menu.setAttribute('role', 'listbox');
        wrapper.appendChild(menu);

        let isOpen = false;
        let isClosing = false;

        function updateOptionsList() {
            menu.innerHTML = '';
            const options = Array.from(selectEl.options);
            const selectedOpt = selectEl.options[selectEl.selectedIndex] || options[0];
            labelSpan.textContent = selectedOpt ? selectedOpt.text : 'Select...';

            options.forEach((opt, idx) => {
                const optItem = document.createElement('div');
                optItem.className = 'custom-select-option' + (opt.selected ? ' is-selected' : '');
                optItem.setAttribute('role', 'option');
                optItem.setAttribute('aria-selected', opt.selected ? 'true' : 'false');
                optItem.dataset.value = opt.value;
                optItem.style.animationDelay = `${Math.min(idx * 16, 200)}ms`;

                const optText = document.createElement('span');
                optText.textContent = opt.text;
                optItem.appendChild(optText);

                if (opt.selected) {
                    const checkIcon = document.createElement('span');
                    checkIcon.className = 'custom-select-check';
                    checkIcon.innerHTML = `
                        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="20 6 9 17 4 12"></polyline>
                        </svg>
                    `;
                    optItem.appendChild(checkIcon);
                }

                optItem.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    selectEl.value = opt.value;
                    selectEl.dispatchEvent(new Event('change', { bubbles: true }));
                    updateOptionsList();
                    closeDropdown();
                    trigger.focus();
                });

                menu.appendChild(optItem);
            });
        }

        function openDropdown() {
            if (isOpen || isClosing) return;

            // Close other open dropdowns first
            document.querySelectorAll('.custom-select-wrapper.is-open').forEach(w => {
                if (w !== wrapper && w._closeDropdown) w._closeDropdown();
            });

            isOpen = true;
            wrapper.classList.add('is-open');
            trigger.setAttribute('aria-expanded', 'true');
            menu.classList.remove('is-closing');
            menu.classList.add('is-entering');
        }

        function closeDropdown() {
            if (!isOpen || isClosing) return;
            isClosing = true;
            trigger.setAttribute('aria-expanded', 'false');
            menu.classList.remove('is-entering');
            menu.classList.add('is-closing');

            // 140ms exit duration (Rule 15: 20% shorter exit)
            setTimeout(() => {
                wrapper.classList.remove('is-open');
                menu.classList.remove('is-closing');
                isOpen = false;
                isClosing = false;
            }, 140);
        }

        wrapper._closeDropdown = closeDropdown;

        trigger.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            if (isOpen) {
                closeDropdown();
            } else {
                openDropdown();
            }
        });

        // Close on outside click
        document.addEventListener('click', (e) => {
            if (isOpen && !wrapper.contains(e.target)) {
                closeDropdown();
            }
        });

        // Keyboard navigation
        trigger.addEventListener('keydown', (e) => {
            if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                if (!isOpen) {
                    openDropdown();
                } else if (e.key === 'Enter' || e.key === ' ') {
                    closeDropdown();
                }
            } else if (e.key === 'Escape' && isOpen) {
                e.preventDefault();
                closeDropdown();
            }
        });

        // Watch for programmatic changes on the original select
        selectEl.addEventListener('change', () => {
            updateOptionsList();
        });

        // MutationObserver watches for options added/removed dynamically
        const observer = new MutationObserver(() => {
            updateOptionsList();
        });
        observer.observe(selectEl, { childList: true, subtree: true, attributes: true });

        // Initial setup
        updateOptionsList();

        // Insert wrapper in DOM
        if (selectEl.parentNode) {
            selectEl.parentNode.insertBefore(wrapper, selectEl);
            wrapper.appendChild(selectEl);
        }
    }

    // Auto-discover all selects on DOMContentLoaded and on dynamic changes
    function enhanceAllSelects() {
        document.querySelectorAll('select:not([data-custom-enhanced="true"])').forEach(sel => {
            // Don't enhance if inside special non-UI elements
            if (sel.closest('.cropper-wrapper')) return;
            initCustomSelect(sel);
        });
    }

    // Tactile button press & Paw stamp ripple feedback (Rule 4 & Playful Paw)
    function attachTactileFeedback() {
        document.addEventListener('pointerdown', (e) => {
            const btn = e.target.closest('.btn, .btn-primary, .btn-danger, .btn-success, .btn-sm, .btn-signout, .filter-tab, .quiz-runner-card');
            if (btn) {
                btn.classList.add('tactile-pressed');
            }

            // Paw Stamp on Button Click for primary actions
            const primaryBtn = e.target.closest('.btn-primary, [data-paw-stamp], .btn-stamp');
            if (primaryBtn && primaryBtn.tagName !== 'INPUT') {
                const rect = primaryBtn.getBoundingClientRect();
                const x = (e.clientX && e.clientX > 0) ? (e.clientX - rect.left) : (rect.width / 2);
                const y = (e.clientY && e.clientY > 0) ? (e.clientY - rect.top) : (rect.height / 2);

                const stamp = document.createElement('span');
                stamp.className = 'paw-stamp-ripple';
                stamp.style.left = `${x}px`;
                stamp.style.top = `${y}px`;
                stamp.innerHTML = `
                    <svg viewBox="0 0 24 24" width="28" height="28" fill="currentColor">
                        <ellipse cx="4.6" cy="8.6" rx="1.85" ry="2.6" transform="rotate(-20 4.6 8.6)" />
                        <ellipse cx="9.3" cy="5.2" rx="2.05" ry="3.0" transform="rotate(-6 9.3 5.2)" />
                        <ellipse cx="14.7" cy="5.2" rx="2.05" ry="3.0" transform="rotate(6 14.7 5.2)" />
                        <ellipse cx="19.4" cy="8.6" rx="1.85" ry="2.6" transform="rotate(20 19.4 8.6)" />
                        <path fill-rule="evenodd" clip-rule="evenodd" d="M 12 9.6 C 7 9.6 4.8 12.6 5.5 16.8 C 6.2 20.2 8.8 21.8 12 21.8 C 15.2 21.8 17.8 20.2 18.5 16.8 C 19.2 12.6 17 9.6 12 9.6 Z M 8.8 14.4 C 9.3 13.3 10.5 13.3 11 14.4 C 10.6 14.7 10.2 14.3 9.9 13.9 C 9.6 14.3 9.2 14.7 8.8 14.4 Z M 13 14.4 C 13.5 13.3 14.7 13.3 15.2 14.4 C 14.8 14.7 14.4 14.3 14.1 13.9 C 13.8 14.3 13.4 14.7 13 14.4 Z M 10.2 17.2 C 10.8 19 13.2 19 13.8 17.2 C 13.1 18 10.9 18 10.2 17.2 Z" />
                    </svg>
                `;
                primaryBtn.appendChild(stamp);
                stamp.addEventListener('animationend', () => stamp.remove(), { once: true });
                setTimeout(() => stamp.remove(), 600);
            }
        });

        document.addEventListener('pointerup', () => {
            document.querySelectorAll('.tactile-pressed').forEach(el => {
                el.classList.remove('tactile-pressed');
            });
        });

        document.addEventListener('pointercancel', () => {
            document.querySelectorAll('.tactile-pressed').forEach(el => {
                el.classList.remove('tactile-pressed');
            });
        });
    }

    // Responsive Mobile Navigation Drawer & Sticky Mobile Header
    function initMobileNavigation() {
        const aside = document.querySelector('aside');
        if (!aside || document.getElementById('mobileTopBar')) return;

        // 1. Create Mobile Top Bar
        const topBar = document.createElement('header');
        topBar.className = 'mobile-top-bar';
        topBar.id = 'mobileTopBar';
        topBar.innerHTML = `
            <div class="mobile-top-brand">
                <div class="mobile-brand-mark">
                    <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
                        <ellipse cx="4.8" cy="8.5" rx="1.8" ry="2.6" transform="rotate(-20 4.8 8.5)" />
                        <ellipse cx="9.5" cy="5.2" rx="2" ry="2.9" transform="rotate(-6 9.5 5.2)" />
                        <ellipse cx="14.5" cy="5.2" rx="2" ry="2.9" transform="rotate(6 14.5 5.2)" />
                        <ellipse cx="19.2" cy="8.5" rx="1.8" ry="2.6" transform="rotate(20 19.2 8.5)" />
                        <path d="M12 9.8c-3.6 0-6.8 2.4-6.8 6.2 0 3.2 2.7 5.5 6.8 5.5s6.8-2.3 6.8-5.5c0-3.8-3.2-6.2-6.8-6.2z" />
                    </svg>
                </div>
                <span class="mobile-brand-title">Happy Paws</span>
            </div>
            <button type="button" class="mobile-menu-btn" id="mobileMenuBtn" aria-label="Toggle navigation menu" aria-expanded="false">
                <span class="hamburger-bar"></span>
                <span class="hamburger-bar"></span>
                <span class="hamburger-bar"></span>
            </button>
        `;

        document.body.insertBefore(topBar, document.body.firstChild);

        // 2. Create Backdrop
        const backdrop = document.createElement('div');
        backdrop.className = 'mobile-nav-backdrop';
        backdrop.id = 'mobileNavBackdrop';
        document.body.appendChild(backdrop);

        // 3. Add Close Button to Aside Header
        let closeBtn = aside.querySelector('.mobile-aside-close');
        if (!closeBtn) {
            closeBtn = document.createElement('button');
            closeBtn.type = 'button';
            closeBtn.className = 'mobile-aside-close';
            closeBtn.id = 'mobileAsideClose';
            closeBtn.setAttribute('aria-label', 'Close navigation menu');
            closeBtn.innerHTML = `
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <line x1="18" y1="6" x2="6" y2="18"></line>
                    <line x1="6" y1="6" x2="18" y2="18"></line>
                </svg>
            `;
            const brandBlock = aside.querySelector('.brand-block');
            if (brandBlock) {
                brandBlock.appendChild(closeBtn);
            } else {
                aside.insertBefore(closeBtn, aside.firstChild);
            }
        }

        function openMenu() {
            aside.classList.add('mobile-drawer-open');
            backdrop.classList.add('is-active');
            document.body.classList.add('mobile-nav-active');
            const btn = document.getElementById('mobileMenuBtn');
            if (btn) btn.setAttribute('aria-expanded', 'true');
        }

        function closeMenu() {
            aside.classList.remove('mobile-drawer-open');
            backdrop.classList.remove('is-active');
            document.body.classList.remove('mobile-nav-active');
            const btn = document.getElementById('mobileMenuBtn');
            if (btn) btn.setAttribute('aria-expanded', 'false');
        }

        const menuBtn = document.getElementById('mobileMenuBtn');
        if (menuBtn) {
            menuBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                if (aside.classList.contains('mobile-drawer-open')) {
                    closeMenu();
                } else {
                    openMenu();
                }
            });
        }

        if (closeBtn) {
            closeBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                closeMenu();
            });
        }

        backdrop.addEventListener('click', closeMenu);

        // Close on link navigation or Escape key
        aside.querySelectorAll('nav a, .btn-signout').forEach(link => {
            link.addEventListener('click', () => {
                if (window.innerWidth <= 900) {
                    closeMenu();
                }
            });
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && aside.classList.contains('mobile-drawer-open')) {
                closeMenu();
            }
        });

        window.addEventListener('resize', () => {
            if (window.innerWidth > 900 && aside.classList.contains('mobile-drawer-open')) {
                closeMenu();
            }
        });
    }

    // Auto-wrap data tables in smooth horizontal scrolling wrappers
    function makeTablesResponsive() {
        document.querySelectorAll('table.data-table').forEach(table => {
            if (!table.parentElement.classList.contains('data-table-responsive')) {
                const wrapper = document.createElement('div');
                wrapper.className = 'data-table-responsive';
                table.parentNode.insertBefore(wrapper, table);
                wrapper.appendChild(table);
            }
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            initMobileNavigation();
            makeTablesResponsive();
            enhanceAllSelects();
            attachTactileFeedback();
            initPeekingCompanion();
        });
    } else {
        initMobileNavigation();
        makeTablesResponsive();
        enhanceAllSelects();
        attachTactileFeedback();
        initPeekingCompanion();
    }

    // Continuously observe DOM for newly added select elements, tables & sidebar companion
    const pageObserver = new MutationObserver(() => {
        initMobileNavigation();
        makeTablesResponsive();
        enhanceAllSelects();
        initPeekingCompanion();
    });
    pageObserver.observe(document.documentElement, { childList: true, subtree: true });

    // Cozy Illustrated Sleeping Companion Empty State Component
    function renderCozyEmptyState(opts = {}) {
        const title = opts.title || 'Everyone is tucked in and cozy.';
        const desc = opts.desc || 'Check back soon for new arrivals!';
        const actionBtn = (opts.actionText && opts.actionHref) 
            ? `<div style="margin-top: 14px;"><a href="${opts.actionHref}" class="btn btn-sm btn-primary">${opts.actionText}</a></div>`
            : '';

        const svg = `
            <svg class="cozy-sleeping-svg" viewBox="0 0 160 120" width="140" height="105" fill="none" xmlns="http://www.w3.org/2000/svg">
                <ellipse cx="80" cy="85" rx="66" ry="20" fill="#F7F1EA" stroke="#E8DFD5" stroke-width="1.5" stroke-dasharray="4 4" />
                <g class="sleeping-companion-body">
                    <path d="M42 78 C36 66 41 48 56 41 C72 34 100 36 112 50 C122 62 122 76 114 84 C106 92 88 93 72 91 C58 89 46 86 42 78 Z" fill="#FFFDFB" stroke="#C45A32" stroke-width="2.2" stroke-linejoin="round" />
                    <path d="M58 44 C70 38 94 40 106 50 C96 56 74 54 58 44 Z" fill="rgba(196, 90, 50, 0.12)" />
                    <path d="M48 46 C44 38 46 32 52 33 C58 34 56 42 54 46" fill="#F5E8DC" stroke="#C45A32" stroke-width="2" stroke-linecap="round" />
                    <path d="M60 42 C58 33 62 27 68 29 C73 31 70 39 66 43" fill="#F5E8DC" stroke="#C45A32" stroke-width="2" stroke-linecap="round" />
                    <path d="M114 82 C124 80 128 70 124 62 C120 56 114 60 112 66 C110 72 112 78 114 82" fill="#FFFDFB" stroke="#C45A32" stroke-width="2.2" stroke-linecap="round" />
                    <ellipse cx="64" cy="84" rx="8" ry="5" fill="#FFFFFF" stroke="#C45A32" stroke-width="1.8" />
                    <line x1="62" y1="82" x2="62" y2="86" stroke="#C45A32" stroke-width="1.4" stroke-linecap="round" />
                    <line x1="66" y1="82" x2="66" y2="86" stroke="#C45A32" stroke-width="1.4" stroke-linecap="round" />
                    <ellipse cx="79" cy="86" rx="7.5" ry="4.5" fill="#FFFFFF" stroke="#C45A32" stroke-width="1.8" />
                    <line x1="77" y1="84" x2="77" y2="88" stroke="#C45A32" stroke-width="1.4" stroke-linecap="round" />
                    <line x1="81" y1="84" x2="81" y2="88" stroke="#C45A32" stroke-width="1.4" stroke-linecap="round" />
                    <path d="M48 62 C44 66 45 72 50 74 C55 75 60 73 62 68" fill="#FFFFFF" stroke="#C45A32" stroke-width="1.8" stroke-linecap="round" />
                    <path d="M47 65 C48 64 50 64 51 65 C50.5 67 48.5 68 47 65 Z" fill="#C45A32" />
                    <path d="M54 57 C57 61 61 61 64 57" stroke="#C45A32" stroke-width="2" stroke-linecap="round" />
                </g>
                <g class="sleeping-zzz">
                    <path d="M102 26 H108 L102 33 H108" stroke="#C45A32" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" opacity="0.75" />
                    <path d="M113 16 H118 L113 22 H118" stroke="#C45A32" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" opacity="0.5" />
                </g>
            </svg>
        `;

        if (opts.isTable) {
            return `
                <tr>
                    <td colspan="${opts.colSpan || 6}" style="padding: 24px 16px;">
                        <div class="cozy-empty-state cozy-empty-state-table">
                            ${svg}
                            <div class="cozy-empty-title">${title}</div>
                            <div class="cozy-empty-desc">${desc}</div>
                            ${actionBtn}
                        </div>
                    </td>
                </tr>
            `;
        }

        return `
            <div class="cozy-empty-state" style="${opts.fullWidth ? 'grid-column: 1 / -1;' : ''}">
                ${svg}
                <div class="cozy-empty-title">${title}</div>
                <div class="cozy-empty-desc">${desc}</div>
                ${actionBtn}
            </div>
        `;
    }

    // Paw-Step Loading Animation Component
    function renderPawStepLoader(text = 'Loading...', opts = {}) {
        const pawSvg = `
            <svg viewBox="0 0 24 24" width="22" height="22">
                <ellipse cx="4.6" cy="8.6" rx="1.85" ry="2.6" transform="rotate(-20 4.6 8.6)" fill="currentColor" />
                <ellipse cx="9.3" cy="5.2" rx="2.05" ry="3.0" transform="rotate(-6 9.3 5.2)" fill="currentColor" />
                <ellipse cx="14.7" cy="5.2" rx="2.05" ry="3.0" transform="rotate(6 14.7 5.2)" fill="currentColor" />
                <ellipse cx="19.4" cy="8.6" rx="1.85" ry="2.6" transform="rotate(20 19.4 8.6)" fill="currentColor" />
                <path fill-rule="evenodd" clip-rule="evenodd" d="M 12 9.6 C 7 9.6 4.8 12.6 5.5 16.8 C 6.2 20.2 8.8 21.8 12 21.8 C 15.2 21.8 17.8 20.2 18.5 16.8 C 19.2 12.6 17 9.6 12 9.6 Z M 8.8 14.4 C 9.3 13.3 10.5 13.3 11 14.4 C 10.6 14.7 10.2 14.3 9.9 13.9 C 9.6 14.3 9.2 14.7 8.8 14.4 Z M 13 14.4 C 13.5 13.3 14.7 13.3 15.2 14.4 C 14.8 14.7 14.4 14.3 14.1 13.9 C 13.8 14.3 13.4 14.7 13 14.4 Z M 10.2 17.2 C 10.8 19 13.2 19 13.8 17.2 C 13.1 18 10.9 18 10.2 17.2 Z" fill="currentColor" />
            </svg>
        `;

        const content = `
            <div class="paw-loader-container" role="status" aria-live="polite">
                <div class="paw-step-loader" aria-hidden="true">
                    <span class="paw-step-pad">${pawSvg}</span>
                    <span class="paw-step-pad">${pawSvg}</span>
                    <span class="paw-step-pad">${pawSvg}</span>
                </div>
                ${text ? `<div class="paw-loader-text">${text}</div>` : ''}
            </div>
        `;

        if (opts.isTable) {
            return `
                <tr>
                    <td colspan="${opts.colSpan || 6}" style="text-align: center; padding: 24px 16px;">
                        ${content}
                    </td>
                </tr>
            `;
        }

        if (opts.fullWidth) {
            return `<div style="grid-column: 1 / -1; width: 100%; text-align: center; padding: 24px 16px;">${content}</div>`;
        }

        return content;
    }

    // Sidebar Peeking Companion Component
    function petCompanion() {
        const heart = document.getElementById('companionHeart');
        const wrap = document.getElementById('peekingCompanion');

        if (wrap) {
            wrap.classList.add('is-wagging');
            setTimeout(() => wrap.classList.remove('is-wagging'), 900);
        }

        if (heart) {
            heart.classList.remove('floating');
            void heart.offsetWidth; // Force reflow
            heart.classList.add('floating');
        }
    }

    function getCompanionMarkup() {
        return `
            <div class="sidebar-companion-wrap" id="peekingCompanion" title="Your loyal shelter companion" onclick="petCompanion()">
                <div class="companion-heart-float" id="companionHeart">
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="#C45A32">
                        <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
                    </svg>
                </div>
                <svg class="peeking-pup-svg" viewBox="0 0 160 56" width="136" height="48" aria-hidden="true">
                    <defs>
                        <radialGradient id="pupBlush" cx="50%" cy="50%" r="50%">
                            <stop offset="0%" stop-color="#FDBA8C" stop-opacity="0.7"/>
                            <stop offset="100%" stop-color="#FDBA8C" stop-opacity="0"/>
                        </radialGradient>
                    </defs>
                    <g class="pup-tail">
                        <path d="M 112 30 C 122 20 126 8 118 3 C 112 -1 107 5 109 16 Z" fill="#E06E44" stroke="#C45A32" stroke-width="1.8" stroke-linejoin="round"/>
                        <path d="M 118 3 C 115 2 112 4 110 7 C 113 9 116 7 118 3 Z" fill="#FFFDFB"/>
                    </g>
                    <g class="pup-ear-left">
                        <path d="M 52 20 C 38 18 31 32 35 44 C 39 50 47 49 52 42 C 54 38 55 28 52 20 Z" fill="#E06E44" stroke="#C45A32" stroke-width="1.8" stroke-linejoin="round"/>
                        <path d="M 39 34 C 37 41 42 45 45 42 C 48 39 48 32 44 30 C 42 30 40 31 39 34 Z" fill="#C45A32" opacity="0.35"/>
                    </g>
                    <g class="pup-ear-right">
                        <path d="M 108 20 C 122 18 129 32 125 44 C 121 50 113 49 108 42 C 106 38 105 28 108 20 Z" fill="#E06E44" stroke="#C45A32" stroke-width="1.8" stroke-linejoin="round"/>
                        <path d="M 121 34 C 123 41 118 45 115 42 C 112 39 112 32 116 30 C 118 30 120 31 121 34 Z" fill="#C45A32" opacity="0.35"/>
                    </g>
                    <path d="M 51 30 C 51 12 62 6 80 6 C 98 6 109 12 109 30 C 109 44 102 52 80 52 C 58 52 51 44 51 30 Z" fill="#FFFDFB" stroke="#C45A32" stroke-width="1.8" stroke-linejoin="round"/>
                    <path d="M 77 7 C 78 16 75 23 72 28 C 76 26 84 26 88 28 C 85 23 82 16 83 7 Z" fill="#FDF3ED"/>
                    <circle cx="61" cy="36" r="6" fill="url(#pupBlush)"/>
                    <circle cx="99" cy="36" r="6" fill="url(#pupBlush)"/>
                    <g class="pup-eye">
                        <ellipse cx="66" cy="25" rx="3.8" ry="4.8" fill="#2D231E"/>
                        <circle cx="64.8" cy="23.2" r="1.5" fill="#FFFDFB"/>
                        <circle cx="67.2" cy="26.8" r="0.7" fill="#FFFDFB"/>
                        <path d="M 62 18 C 64 16 68 17 70 19" stroke="#C45A32" stroke-width="1.4" stroke-linecap="round" fill="none"/>
                    </g>
                    <g class="pup-eye">
                        <ellipse cx="94" cy="25" rx="3.8" ry="4.8" fill="#2D231E"/>
                        <circle cx="92.8" cy="23.2" r="1.5" fill="#FFFDFB"/>
                        <circle cx="95.2" cy="26.8" r="0.7" fill="#FFFDFB"/>
                        <path d="M 98 18 C 96 16 92 17 90 19" stroke="#C45A32" stroke-width="1.4" stroke-linecap="round" fill="none"/>
                    </g>
                    <ellipse cx="80" cy="37" rx="10.5" ry="7.5" fill="#FDF3ED" stroke="#E8DFD5" stroke-width="1"/>
                    <path d="M 77 32.5 C 77 31 83 31 83 32.5 C 83 35 81 36.5 80 36.5 C 79 36.5 77 35 77 32.5 Z" fill="#C45A32"/>
                    <circle cx="78.8" cy="32.2" r="0.7" fill="#FFFDFB" opacity="0.9"/>
                    <path d="M 77 36.5 C 77 39 75 40 73.5 39 M 80 38.5 L 80 38 M 80 38 C 81.5 40.5 84.5 40 86.5 39" stroke="#C45A32" stroke-width="1.4" stroke-linecap="round" fill="none"/>
                    <g class="pup-paw-left">
                        <path d="M 50 54 C 50 46 57 44 63 44 C 69 44 71 47 70 54 Z" fill="#FFFDFB" stroke="#C45A32" stroke-width="1.8" stroke-linejoin="round"/>
                        <line x1="56" y1="48" x2="56" y2="54" stroke="#C45A32" stroke-width="1.2" stroke-linecap="round"/>
                        <line x1="62" y1="47" x2="62" y2="54" stroke="#C45A32" stroke-width="1.2" stroke-linecap="round"/>
                    </g>
                    <g class="pup-paw-right">
                        <path d="M 90 54 C 89 47 91 44 97 44 C 103 44 110 46 110 54 Z" fill="#FFFDFB" stroke="#C45A32" stroke-width="1.8" stroke-linejoin="round"/>
                        <line x1="98" y1="47" x2="98" y2="54" stroke="#C45A32" stroke-width="1.2" stroke-linecap="round"/>
                        <line x1="104" y1="48" x2="104" y2="54" stroke="#C45A32" stroke-width="1.2" stroke-linecap="round"/>
                    </g>
                </svg>
            </div>
        `;
    }

    function initPeekingCompanion() {
        const userCard = document.querySelector('aside .user-card');
        if (!userCard) return;

        let existing = document.getElementById('peekingCompanion');
        if (!existing) {
            userCard.insertAdjacentHTML('beforebegin', getCompanionMarkup());
        }
    }

    // Expose utilities globally
    window.enhanceCustomSelects = enhanceAllSelects;
    window.renderCozyEmptyState = renderCozyEmptyState;
    window.renderPawStepLoader = renderPawStepLoader;
    window.petCompanion = petCompanion;
    window.initPeekingCompanion = initPeekingCompanion;
})();
