// State
let currentTab = 'subscriptions';
let currentPage = 0;
let isLoading = false;
let selectedCategoryId = null;

// Client-side video cache for instant tab switching
const videoCache = {
    subscriptions: { videos: [], pagesLoaded: 0 },
    discover: { videos: [], pagesLoaded: 0 },
    saved: { videos: [], pagesLoaded: 0 }
};

// Summary cache key prefix for localStorage
const SUMMARY_CACHE_PREFIX = 'yt_summary_';
const SUMMARY_CACHE_TTL_DAYS = 7;

// Set of saved video URLs (loaded on startup)
let savedVideoUrls = new Set();

// DOM Elements
const videoGrid = document.getElementById('video-grid');
const loading = document.getElementById('loading');
const loadMoreBtn = document.getElementById('load-more');
const accountInfo = document.getElementById('account-info');
const summaryModal = document.getElementById('summary-modal');
const summaryTitle = document.getElementById('summary-title');
const summaryLoading = document.getElementById('summary-loading');
const summaryText = document.getElementById('summary-text');
const tabs = document.querySelectorAll('.tab');
const refreshBtn = document.getElementById('refresh-btn');
const categoryFilter = document.getElementById('category-filter');
const categoryChips = document.getElementById('category-chips');

// Initialize
document.addEventListener('DOMContentLoaded', async () => {
    await loadSavedVideoUrls();
    loadAccountInfo();
    loadCategories();
    loadVideos();
    setupEventListeners();
    cleanupExpiredSummaries();
});

// Load saved video URLs for showing saved indicators
async function loadSavedVideoUrls() {
    try {
        const response = await fetch('/api/videos/saved/urls');
        if (response.ok) {
            const urls = await response.json();
            savedVideoUrls = new Set(urls);
            console.log(`Loaded ${savedVideoUrls.size} saved video URLs`);
        }
    } catch (error) {
        console.error('Failed to load saved video URLs:', error);
    }
}

// Load video categories for Discover tab
async function loadCategories() {
    try {
        const response = await fetch('/api/categories');
        if (response.ok) {
            const categories = await response.json();
            renderCategoryChips(categories);
        }
    } catch (error) {
        console.error('Failed to load categories:', error);
    }
}

function renderCategoryChips(categories) {
    // Add "All" chip first
    let html = '<button class="category-chip active" data-category-id="">All</button>';
    categories.forEach(cat => {
        html += `<button class="category-chip" data-category-id="${cat.id}">${cat.title}</button>`;
    });
    categoryChips.innerHTML = html;

    // Add click handlers
    categoryChips.querySelectorAll('.category-chip').forEach(chip => {
        chip.addEventListener('click', () => {
            const catId = chip.dataset.categoryId || null;
            if (catId === selectedCategoryId) return;

            // Update active state
            categoryChips.querySelectorAll('.category-chip').forEach(c => c.classList.remove('active'));
            chip.classList.add('active');

            selectedCategoryId = catId;

            // Clear discover cache and reload
            videoCache.discover = { videos: [], pagesLoaded: 0 };
            currentPage = 0;
            videoGrid.innerHTML = '';
            loadVideos();
        });
    });
}

function setupEventListeners() {
    // Tab switching
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const newTab = tab.dataset.tab;
            if (newTab !== currentTab) {
                tabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                currentTab = newTab;
                currentPage = 0;

                // Show/hide refresh button and category filter
                if (refreshBtn) {
                    refreshBtn.style.display = newTab === 'saved' ? 'none' : 'flex';
                }
                if (categoryFilter) {
                    if (newTab === 'discover') {
                        categoryFilter.classList.add('visible');
                    } else {
                        categoryFilter.classList.remove('visible');
                    }
                }

                // Try to render from cache first
                if (videoCache[newTab].videos.length > 0) {
                    renderVideosFromCache(newTab);
                } else {
                    videoGrid.innerHTML = '';
                    loadVideos();
                }
            }
        });
    });

    // Load more
    loadMoreBtn.addEventListener('click', () => {
        currentPage++;
        loadVideos(true);
    });

    loadMoreBtn.dataset.originalText = loadMoreBtn.textContent;

    // Refresh button
    if (refreshBtn) {
        refreshBtn.addEventListener('click', () => {
            forceRefresh();
        });
    }

    // Close modal
    document.querySelector('.close-modal').addEventListener('click', closeModal);
    summaryModal.addEventListener('click', (e) => {
        if (e.target === summaryModal) closeModal();
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeModal();
    });

    // Modal regenerate button
    const modalRegenerateBtn = document.getElementById('modal-regenerate-btn');
    if (modalRegenerateBtn) {
        modalRegenerateBtn.addEventListener('click', regenerateFromModal);
    }
}

// Render videos from client-side cache
function renderVideosFromCache(tab) {
    const cache = videoCache[tab];
    videoGrid.innerHTML = '';

    cache.videos.forEach(video => {
        videoGrid.appendChild(createVideoCard(video));
    });

    currentPage = cache.pagesLoaded - 1;
    loadMoreBtn.classList.remove('hidden');

    console.log(`Rendered ${cache.videos.length} videos from cache for tab: ${tab}`);
}

// Force refresh
async function forceRefresh() {
    if (currentTab === 'discover') {
        videoCache.discover = { videos: [], pagesLoaded: 0 };
    } else {
        videoCache[currentTab] = { videos: [], pagesLoaded: 0 };
    }
    currentPage = 0;
    videoGrid.innerHTML = '';

    if (refreshBtn) {
        refreshBtn.disabled = true;
        refreshBtn.classList.add('refreshing');
    }

    await loadVideos(false, true);

    if (refreshBtn) {
        refreshBtn.disabled = false;
        refreshBtn.classList.remove('refreshing');
    }
}

async function loadAccountInfo() {
    try {
        const response = await fetch('/api/account');
        if (response.ok) {
            const account = await response.json();
            accountInfo.innerHTML = `
                <span class="account-name">${account.name || 'YouTube User'}</span>
                <span class="account-handle">${account.handle || ''}</span>
            `;
        }
    } catch (error) {
        console.error('Failed to load account info:', error);
    }
}

// Map SavedVideo entity to standard video card format
function mapSavedVideoToVideoFormat(savedVideo) {
    return {
        videoUrl: savedVideo.videoUrl,
        title: savedVideo.title,
        channelName: savedVideo.channelName,
        thumbnailUrl: savedVideo.thumbnailUrl,
        duration: savedVideo.duration,
        _savedSummary: savedVideo.aiSummary,
        _isSaved: true
    };
}

async function loadVideos(append = false, forceRefresh = false) {
    if (isLoading) return;
    isLoading = true;

    if (!append) {
        loading.classList.remove('hidden');
        loadMoreBtn.classList.add('hidden');
    } else {
        loadMoreBtn.disabled = true;
        loadMoreBtn.classList.add('loading');
        loadMoreBtn.innerHTML = '<span class="btn-spinner"></span> Loading...';
    }

    try {
        let url;
        if (currentTab === 'saved') {
            url = `/api/videos/saved?page=${currentPage}`;
        } else if (currentTab === 'discover') {
            url = `/api/videos?tab=discover&page=${currentPage}`;
            if (selectedCategoryId) {
                url += `&categoryId=${selectedCategoryId}`;
            }
            if (forceRefresh) {
                url += '&forceRefresh=true';
            }
        } else {
            url = `/api/videos?tab=${currentTab}&page=${currentPage}`;
            if (forceRefresh) {
                url += '&forceRefresh=true';
            }
        }

        const response = await fetch(url);
        if (!response.ok) throw new Error('Failed to load videos');

        let videos = await response.json();

        // Map saved videos to standard format
        if (currentTab === 'saved') {
            videos = videos.map(mapSavedVideoToVideoFormat);
        }

        // Update client-side cache
        if (!append || forceRefresh) {
            videoCache[currentTab].videos = [...videos];
            videoCache[currentTab].pagesLoaded = 1;
        } else {
            videoCache[currentTab].videos.push(...videos);
            videoCache[currentTab].pagesLoaded = currentPage + 1;
        }

        if (!append) {
            videoGrid.innerHTML = '';
        }

        videos.forEach(video => {
            videoGrid.appendChild(createVideoCard(video));
        });

        if (videos.length > 0) {
            loadMoreBtn.classList.remove('hidden');
        } else {
            loadMoreBtn.classList.add('hidden');
        }

        console.log(`Loaded ${videos.length} videos, cache now has ${videoCache[currentTab].videos.length} total`);

    } catch (error) {
        console.error('Failed to load videos:', error);
        if (!append) {
            videoGrid.innerHTML = `
                <div class="error-message">
                    <p>Failed to load videos. Please check your YouTube API configuration.</p>
                    <p style="font-size: 12px; color: #aaa; margin-top: 8px;">
                        Make sure YOUTUBE_CLIENT_ID and YOUTUBE_CLIENT_SECRET are set.
                    </p>
                </div>
            `;
        }
    } finally {
        loading.classList.add('hidden');
        isLoading = false;

        loadMoreBtn.disabled = false;
        loadMoreBtn.classList.remove('loading');
        loadMoreBtn.textContent = loadMoreBtn.dataset.originalText || 'Load More';
    }
}

function createVideoCard(video) {
    const card = document.createElement('div');
    card.className = 'video-card';

    let cachedSummary = video._savedSummary || getCachedSummary(video.videoUrl);
    const hasCachedSummary = cachedSummary !== null;

    if (video._savedSummary && !getCachedSummary(video.videoUrl)) {
        cacheSummary(video.videoUrl, video._savedSummary);
    }

    const isVideoSaved = video._isSaved || savedVideoUrls.has(video.videoUrl);
    const videoId = extractVideoId(video.videoUrl);

    card.innerHTML = `
        <div class="card-inner">
            <div class="card-front">
                <div class="thumbnail-container">
                    <img class="thumbnail" src="${video.thumbnailUrl || ''}" alt="${video.title || ''}" loading="lazy">
                    ${video.duration ? `<span class="duration">${video.duration}</span>` : ''}
                </div>
                <div class="video-info">
                    <h3 class="video-title">${video.title || 'Untitled'}</h3>
                    <p class="channel-name">${video.channelName || ''}</p>
                </div>
                <div class="card-actions">
                    <button class="action-btn summary-btn ${hasCachedSummary ? 'has-cache' : ''}"
                            data-video-url="${video.videoUrl}"
                            data-title="${escapeHtml(video.title)}">
                        ${hasCachedSummary ? 'View Summary' : 'AI Summary'}
                    </button>
                    ${currentTab === 'saved' ? `
                        <button class="action-btn open-btn" data-video-url="${video.videoUrl}" title="Open video in new tab">
                            Open
                        </button>
                        <button class="action-btn delete-btn" data-video-url="${video.videoUrl}" title="Delete from saved">
                            Delete
                        </button>
                    ` : `
                        <button class="action-btn save-btn ${isVideoSaved ? 'saved' : ''}"
                                data-video-url="${video.videoUrl}"
                                data-title="${escapeHtml(video.title)}"
                                data-channel="${escapeHtml(video.channelName || '')}"
                                data-thumbnail="${video.thumbnailUrl || ''}"
                                data-duration="${video.duration || ''}"
                                data-video-id="${videoId}"
                                ${isVideoSaved ? 'disabled' : ''}>
                            ${isVideoSaved ? 'Saved' : 'Save'}
                        </button>
                    `}
                </div>
            </div>
            <div class="card-back">
                <div class="summary-header">
                    <h4 class="summary-card-title">${truncateText(video.title || 'Summary', 50)}</h4>
                    <button class="flip-back-btn" title="Back to video">
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M19 12H5M12 19l-7-7 7-7"/>
                        </svg>
                    </button>
                </div>
                <div class="summary-preview"></div>
                <div class="card-back-actions">
                    <button class="regenerate-btn" data-video-url="${video.videoUrl}" data-title="${escapeHtml(video.title)}" title="Generate new summary">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M23 4v6h-6M1 20v-6h6"/>
                            <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                        </svg>
                        New
                    </button>
                    <button class="expand-summary-btn" data-video-url="${video.videoUrl}" data-title="${escapeHtml(video.title)}">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                            <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
                        </svg>
                        Expand
                    </button>
                </div>
            </div>
        </div>
    `;

    // Summary button click
    const summaryBtn = card.querySelector('.summary-btn');
    summaryBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        loadSummaryAndFlip(card, video.videoUrl, video.title);
    });

    // Flip back button
    card.querySelector('.flip-back-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        card.classList.remove('flipped');
    });

    // Expand button
    card.querySelector('.expand-summary-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        const summary = card.querySelector('.summary-preview').dataset.fullSummary;
        openSummaryModal(video.title, summary, video.videoUrl);
    });

    // Regenerate button
    card.querySelector('.regenerate-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        regenerateSummary(card, video.videoUrl, video.title);
    });

    // Button handlers based on current tab
    if (currentTab === 'saved') {
        card.querySelector('.open-btn').addEventListener('click', (e) => {
            e.stopPropagation();
            window.open(video.videoUrl, '_blank');
        });

        card.querySelector('.delete-btn').addEventListener('click', (e) => {
            e.stopPropagation();
            deleteVideoFromDatabase(e.target, video.videoUrl, video.title);
        });
    } else {
        card.querySelector('.save-btn').addEventListener('click', (e) => {
            e.stopPropagation();
            saveVideoToDatabase(e.target);
        });
    }

    // Pre-populate back if cached
    if (hasCachedSummary) {
        const previewEl = card.querySelector('.summary-preview');
        previewEl.textContent = truncateText(cachedSummary, 300);
        previewEl.dataset.fullSummary = cachedSummary;
    }

    return card;
}

// Track pending summary requests
const pendingSummaries = new Map();

async function loadSummaryAndFlip(card, videoUrl, title) {
    const summaryBtn = card.querySelector('.summary-btn');
    const previewEl = card.querySelector('.summary-preview');

    const cachedSummary = getCachedSummary(videoUrl);
    if (cachedSummary) {
        previewEl.textContent = truncateText(cachedSummary, 300);
        previewEl.dataset.fullSummary = cachedSummary;
        card.classList.add('flipped');
        return;
    }

    if (pendingSummaries.has(videoUrl)) {
        console.log('Summary already pending for:', videoUrl);
        return;
    }

    summaryBtn.disabled = true;
    summaryBtn.innerHTML = '<span class="btn-spinner"></span> Queuing...';

    try {
        const response = await fetch('/api/summary/request', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ videoUrl, videoTitle: title })
        });

        if (!response.ok) throw new Error('Failed to submit summary request');

        const data = await response.json();
        const { requestId, status, summary, queuePosition } = data;

        if (status === 'COMPLETED' && summary) {
            handleSummaryComplete(card, videoUrl, summary, summaryBtn, previewEl);
            return;
        }

        updateSummaryButtonStatus(summaryBtn, status, queuePosition);

        const intervalId = setInterval(() => {
            pollSummaryStatus(requestId, card, videoUrl, summaryBtn, previewEl);
        }, 2000);

        pendingSummaries.set(videoUrl, { requestId, intervalId, card });
        console.log(`Summary request queued: ${requestId} (position ${queuePosition})`);

    } catch (error) {
        console.error('Failed to submit summary request:', error);
        summaryBtn.disabled = false;
        summaryBtn.textContent = 'AI Summary';
        alert('Failed to request AI summary. Please try again.');
    }
}

async function pollSummaryStatus(requestId, card, videoUrl, summaryBtn, previewEl) {
    try {
        const response = await fetch(`/api/summary/status/${requestId}`);
        if (!response.ok) return;

        const data = await response.json();
        const { status, summary, queuePosition, error } = data;

        updateSummaryButtonStatus(summaryBtn, status, queuePosition);

        if (status === 'COMPLETED') {
            stopPolling(videoUrl);
            handleSummaryComplete(card, videoUrl, summary, summaryBtn, previewEl);
        } else if (status === 'FAILED') {
            stopPolling(videoUrl);
            summaryBtn.disabled = false;
            summaryBtn.textContent = 'AI Summary';
            summaryBtn.classList.add('error');
            console.error('Summary generation failed:', error);
            alert('Failed to generate summary: ' + (error || 'Unknown error'));
        }
    } catch (error) {
        console.error('Error polling summary status:', error);
    }
}

function handleSummaryComplete(card, videoUrl, summary, summaryBtn, previewEl) {
    const finalSummary = summary || 'No summary available.';

    if (finalSummary && finalSummary !== 'No summary available.') {
        cacheSummary(videoUrl, finalSummary);
    }

    previewEl.textContent = truncateText(finalSummary, 300);
    previewEl.dataset.fullSummary = finalSummary;

    card.classList.add('flipped');

    summaryBtn.disabled = false;
    summaryBtn.classList.remove('error');
    summaryBtn.classList.add('has-cache');
    summaryBtn.textContent = 'View Summary';
}

function updateSummaryButtonStatus(summaryBtn, status, queuePosition) {
    if (status === 'QUEUED') {
        summaryBtn.innerHTML = `<span class="btn-spinner"></span> Queue #${queuePosition}`;
    } else if (status === 'PROCESSING') {
        summaryBtn.innerHTML = '<span class="btn-spinner"></span> Processing...';
    }
}

function stopPolling(videoUrl) {
    const pending = pendingSummaries.get(videoUrl);
    if (pending) {
        clearInterval(pending.intervalId);
        pendingSummaries.delete(videoUrl);
    }
}

function cleanupPendingSummaries() {
    for (const [videoUrl, pending] of pendingSummaries) {
        clearInterval(pending.intervalId);
    }
    pendingSummaries.clear();
}

window.addEventListener('beforeunload', cleanupPendingSummaries);

// ==================== Regenerate Summary ====================

async function regenerateSummary(card, videoUrl, title) {
    const regenerateBtn = card.querySelector('.regenerate-btn');
    const previewEl = card.querySelector('.summary-preview');
    const summaryBtn = card.querySelector('.summary-btn');

    clearCachedSummary(videoUrl);

    regenerateBtn.disabled = true;
    regenerateBtn.classList.add('loading');
    regenerateBtn.innerHTML = '<span class="btn-spinner"></span>';

    previewEl.textContent = 'Generating new summary...';
    previewEl.dataset.fullSummary = '';

    try {
        const response = await fetch('/api/summary/regenerate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ videoUrl, videoTitle: title })
        });

        if (!response.ok) throw new Error('Failed to submit regenerate request');

        const data = await response.json();
        const { requestId, status, queuePosition } = data;

        const intervalId = setInterval(() => {
            pollRegenerateSummaryStatus(requestId, card, videoUrl, regenerateBtn, previewEl, summaryBtn);
        }, 2000);

        pendingSummaries.set(videoUrl, { requestId, intervalId, card });
        updateRegenerateButtonStatus(regenerateBtn, status, queuePosition);

    } catch (error) {
        console.error('Failed to regenerate summary:', error);
        regenerateBtn.disabled = false;
        regenerateBtn.classList.remove('loading');
        regenerateBtn.innerHTML = `
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M23 4v6h-6M1 20v-6h6"/>
                <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
            </svg>
            New
        `;
        previewEl.textContent = 'Failed to regenerate. Try again.';
    }
}

async function pollRegenerateSummaryStatus(requestId, card, videoUrl, regenerateBtn, previewEl, summaryBtn) {
    try {
        const response = await fetch(`/api/summary/status/${requestId}`);
        if (!response.ok) return;

        const data = await response.json();
        const { status, summary, queuePosition, error } = data;

        updateRegenerateButtonStatus(regenerateBtn, status, queuePosition);

        if (status === 'COMPLETED') {
            stopPolling(videoUrl);

            const finalSummary = summary || 'No summary available.';

            if (finalSummary && finalSummary !== 'No summary available.') {
                cacheSummary(videoUrl, finalSummary);
            }

            previewEl.textContent = truncateText(finalSummary, 300);
            previewEl.dataset.fullSummary = finalSummary;

            regenerateBtn.disabled = false;
            regenerateBtn.classList.remove('loading');
            regenerateBtn.innerHTML = `
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M23 4v6h-6M1 20v-6h6"/>
                    <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                </svg>
                New
            `;

            summaryBtn.classList.add('has-cache');
            summaryBtn.textContent = 'View Summary';

            if (currentModalContext.videoUrl === videoUrl) {
                summaryText.textContent = finalSummary;
            }

        } else if (status === 'FAILED') {
            stopPolling(videoUrl);

            regenerateBtn.disabled = false;
            regenerateBtn.classList.remove('loading');
            regenerateBtn.innerHTML = `
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M23 4v6h-6M1 20v-6h6"/>
                    <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                </svg>
                New
            `;

            previewEl.textContent = 'Failed to regenerate: ' + (error || 'Unknown error');
        }
    } catch (error) {
        console.error('Error polling regenerate status:', error);
    }
}

function updateRegenerateButtonStatus(btn, status, queuePosition) {
    if (status === 'QUEUED') {
        btn.innerHTML = `<span class="btn-spinner"></span> #${queuePosition}`;
    } else if (status === 'PROCESSING') {
        btn.innerHTML = '<span class="btn-spinner"></span>';
    }
}

// Regenerate from modal
async function regenerateFromModal() {
    if (!currentModalContext.videoUrl) return;

    const modalRegenerateBtn = document.getElementById('modal-regenerate-btn');

    clearCachedSummary(currentModalContext.videoUrl);

    modalRegenerateBtn.disabled = true;
    modalRegenerateBtn.classList.add('loading');
    summaryText.textContent = 'Generating new summary...';

    try {
        const response = await fetch('/api/summary/regenerate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                videoUrl: currentModalContext.videoUrl,
                videoTitle: currentModalContext.title
            })
        });

        if (!response.ok) throw new Error('Failed to submit regenerate request');

        const data = await response.json();
        const { requestId } = data;

        const checkStatus = async () => {
            const statusResponse = await fetch(`/api/summary/status/${requestId}`);
            const statusData = await statusResponse.json();

            if (statusData.status === 'COMPLETED') {
                const newSummary = statusData.summary || 'No summary available.';

                if (newSummary && newSummary !== 'No summary available.') {
                    cacheSummary(currentModalContext.videoUrl, newSummary);
                }

                summaryText.textContent = newSummary;
                modalRegenerateBtn.disabled = false;
                modalRegenerateBtn.classList.remove('loading');

                if (currentModalContext.card) {
                    const previewEl = currentModalContext.card.querySelector('.summary-preview');
                    if (previewEl) {
                        previewEl.textContent = truncateText(newSummary, 300);
                        previewEl.dataset.fullSummary = newSummary;
                    }
                }

            } else if (statusData.status === 'FAILED') {
                summaryText.textContent = 'Failed to regenerate: ' + (statusData.error || 'Unknown error');
                modalRegenerateBtn.disabled = false;
                modalRegenerateBtn.classList.remove('loading');
            } else {
                setTimeout(checkStatus, 2000);
            }
        };

        checkStatus();

    } catch (error) {
        console.error('Failed to regenerate from modal:', error);
        summaryText.textContent = 'Failed to regenerate. Please try again.';
        modalRegenerateBtn.disabled = false;
        modalRegenerateBtn.classList.remove('loading');
    }
}

function truncateText(text, maxLength) {
    if (!text) return '';
    if (text.length <= maxLength) return text;
    return text.substring(0, maxLength).trim() + '...';
}

function escapeHtml(text) {
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

// ==================== Summary Caching (localStorage) ====================

function getCachedSummary(videoUrl) {
    try {
        const key = SUMMARY_CACHE_PREFIX + btoa(videoUrl);
        const cached = localStorage.getItem(key);
        if (!cached) return null;

        const { summary, timestamp } = JSON.parse(cached);
        const ageInDays = (Date.now() - timestamp) / (1000 * 60 * 60 * 24);

        if (ageInDays >= SUMMARY_CACHE_TTL_DAYS) {
            localStorage.removeItem(key);
            return null;
        }

        return summary;
    } catch (e) {
        console.error('Error reading summary cache:', e);
        return null;
    }
}

function cacheSummary(videoUrl, summary) {
    try {
        const key = SUMMARY_CACHE_PREFIX + btoa(videoUrl);
        const data = {
            summary,
            timestamp: Date.now()
        };
        localStorage.setItem(key, JSON.stringify(data));
    } catch (e) {
        console.error('Error caching summary:', e);
    }
}

function clearCachedSummary(videoUrl) {
    try {
        const key = SUMMARY_CACHE_PREFIX + btoa(videoUrl);
        localStorage.removeItem(key);
    } catch (e) {
        console.error('Error clearing summary cache:', e);
    }
}

function cleanupExpiredSummaries() {
    try {
        const keysToRemove = [];
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            if (key && key.startsWith(SUMMARY_CACHE_PREFIX)) {
                const cached = localStorage.getItem(key);
                if (cached) {
                    const { timestamp } = JSON.parse(cached);
                    const ageInDays = (Date.now() - timestamp) / (1000 * 60 * 60 * 24);
                    if (ageInDays >= SUMMARY_CACHE_TTL_DAYS) {
                        keysToRemove.push(key);
                    }
                }
            }
        }
        keysToRemove.forEach(key => localStorage.removeItem(key));
        if (keysToRemove.length > 0) {
            console.log(`Cleaned up ${keysToRemove.length} expired summary cache entries`);
        }
    } catch (e) {
        console.error('Error cleaning up summary cache:', e);
    }
}

// ==================== Summary Modal ====================

let currentModalContext = { videoUrl: null, title: null, card: null };

function openSummaryModal(title, summary, videoUrl, card = null) {
    currentModalContext = { videoUrl, title, card };

    summaryTitle.textContent = title || 'Video Summary';
    summaryText.textContent = summary || 'No summary available.';
    summaryText.classList.remove('hidden');
    summaryLoading.classList.add('hidden');
    summaryModal.classList.add('active');

    const modalRegenerateBtn = document.getElementById('modal-regenerate-btn');
    if (modalRegenerateBtn) {
        modalRegenerateBtn.disabled = false;
        modalRegenerateBtn.classList.remove('loading');
    }
}

function closeModal() {
    summaryModal.classList.remove('active');
    currentModalContext = { videoUrl: null, title: null, card: null };
}

// ==================== Confirmation Modal ====================

const confirmModal = document.getElementById('confirm-modal');
const confirmTitle = document.getElementById('confirm-title');
const confirmMessage = document.getElementById('confirm-message');
let confirmResolve = null;

function showConfirmModal(title, message) {
    return new Promise((resolve) => {
        confirmTitle.textContent = title;
        confirmMessage.textContent = message;
        confirmResolve = resolve;
        confirmModal.classList.add('active');
    });
}

function closeConfirmModal(result) {
    confirmModal.classList.remove('active');
    if (confirmResolve) {
        confirmResolve(result);
        confirmResolve = null;
    }
}

document.querySelector('.confirm-cancel').addEventListener('click', () => closeConfirmModal(false));
document.querySelector('.confirm-delete').addEventListener('click', () => closeConfirmModal(true));
confirmModal.addEventListener('click', (e) => {
    if (e.target === confirmModal) closeConfirmModal(false);
});

// Extract video ID from YouTube URL
function extractVideoId(videoUrl) {
    if (!videoUrl) return '';
    const match = videoUrl.match(/[?&]v=([^&]+)/);
    return match ? match[1] : '';
}

// Save video to database
async function saveVideoToDatabase(button) {
    if (button.classList.contains('saved')) return;

    const videoUrl = button.dataset.videoUrl;
    const title = button.dataset.title;
    const channelName = button.dataset.channel;
    const thumbnailUrl = button.dataset.thumbnail;
    const duration = button.dataset.duration;
    const videoId = button.dataset.videoId;

    const cachedSummary = getCachedSummary(videoUrl);
    if (!cachedSummary) {
        alert('Please generate an AI Summary first before saving.');
        return;
    }

    const originalText = button.textContent;
    button.textContent = 'Saving...';
    button.disabled = true;

    try {
        const response = await fetch('/api/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                videoUrl,
                videoTitle: title,
                channelName,
                thumbnailUrl,
                duration,
                videoId,
                aiSummary: cachedSummary
            })
        });

        const data = await response.json();

        if (data.success) {
            button.textContent = 'Saved';
            button.classList.add('saved');
            savedVideoUrls.add(videoUrl);
            videoCache.saved = { videos: [], pagesLoaded: 0 };
        } else {
            button.textContent = originalText;
            button.disabled = false;
            alert(data.error || 'Failed to save video. Please try again.');
        }
    } catch (error) {
        console.error('Failed to save video:', error);
        button.textContent = originalText;
        button.disabled = false;
        alert('Failed to save video. Please try again.');
    }
}

// Delete video from database
async function deleteVideoFromDatabase(button, videoUrl, title) {
    const confirmed = await showConfirmModal(
        'Delete Video',
        `Are you sure you want to delete "${title}" from your saved videos?`
    );
    if (!confirmed) return;

    const originalText = button.textContent;
    button.textContent = 'Deleting...';
    button.disabled = true;

    try {
        const response = await fetch(`/api/videos/saved?videoUrl=${encodeURIComponent(videoUrl)}`, {
            method: 'DELETE'
        });

        const data = await response.json();

        if (data.success) {
            savedVideoUrls.delete(videoUrl);
            clearCachedSummary(videoUrl);

            videoCache.saved = { videos: [], pagesLoaded: 0 };
            currentPage = 0;
            videoGrid.innerHTML = '';
            loadVideos();
        } else {
            button.textContent = originalText;
            button.disabled = false;
            alert(data.message || 'Failed to delete video. Please try again.');
        }
    } catch (error) {
        console.error('Failed to delete video:', error);
        button.textContent = originalText;
        button.disabled = false;
        alert('Failed to delete video. Please try again.');
    }
}
