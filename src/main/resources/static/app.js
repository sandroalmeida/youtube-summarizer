// State
let currentTab = 'subscriptions';
let currentPage = 0;
let isLoading = false;

// Client-side video cache for instant tab switching
const videoCache = {
    subscriptions: { videos: [], pagesLoaded: 0 },
    home: { videos: [], pagesLoaded: 0 }
};

// Summary cache key prefix for localStorage
const SUMMARY_CACHE_PREFIX = 'yt_summary_';
const SUMMARY_CACHE_TTL_DAYS = 7;

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

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    loadAccountInfo();
    loadVideos();
    setupEventListeners();
    cleanupExpiredSummaries();
});

function setupEventListeners() {
    // Tab switching - use cache for instant switching
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const newTab = tab.dataset.tab;
            if (newTab !== currentTab) {
                tabs.forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                currentTab = newTab;
                currentPage = 0;

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

    // Load more - always fetch from server
    loadMoreBtn.addEventListener('click', () => {
        currentPage++;
        loadVideos(true);
    });

    // Store original button text
    loadMoreBtn.dataset.originalText = loadMoreBtn.textContent;

    // Refresh button - force refresh from server
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
}

// Render videos from client-side cache (instant tab switching)
function renderVideosFromCache(tab) {
    const cache = videoCache[tab];
    videoGrid.innerHTML = '';

    cache.videos.forEach(video => {
        videoGrid.appendChild(createVideoCard(video));
    });

    // Update page counter to match cache
    currentPage = cache.pagesLoaded - 1;
    loadMoreBtn.classList.remove('hidden');

    console.log(`Rendered ${cache.videos.length} videos from cache for tab: ${tab}`);
}

// Force refresh - clear cache and reload
async function forceRefresh() {
    // Clear client-side cache for current tab
    videoCache[currentTab] = { videos: [], pagesLoaded: 0 };
    currentPage = 0;
    videoGrid.innerHTML = '';

    // Show loading and set refresh button state
    if (refreshBtn) {
        refreshBtn.disabled = true;
        refreshBtn.classList.add('refreshing');
    }

    // Fetch with forceRefresh=true to bypass server cache
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

async function loadVideos(append = false, forceRefresh = false) {
    if (isLoading) return;
    isLoading = true;

    if (!append) {
        loading.classList.remove('hidden');
        loadMoreBtn.classList.add('hidden');
    } else {
        // Show loading state on Load More button
        loadMoreBtn.disabled = true;
        loadMoreBtn.classList.add('loading');
        loadMoreBtn.innerHTML = '<span class="btn-spinner"></span> Loading...';
    }

    try {
        // Build URL with forceRefresh parameter
        let url = `/api/videos?tab=${currentTab}&page=${currentPage}`;
        if (forceRefresh) {
            url += '&forceRefresh=true';
        }

        const response = await fetch(url);
        if (!response.ok) throw new Error('Failed to load videos');

        const videos = await response.json();

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

        // Show/hide load more button
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
                    <p>Failed to load videos. Make sure Chrome is running with remote debugging enabled.</p>
                    <p style="font-size: 12px; color: #aaa; margin-top: 8px;">
                        Run: /Applications/Google\\ Chrome.app/Contents/MacOS/Google\\ Chrome --remote-debugging-port=9222
                    </p>
                </div>
            `;
        }
    } finally {
        loading.classList.add('hidden');
        isLoading = false;

        // Reset Load More button state
        loadMoreBtn.disabled = false;
        loadMoreBtn.classList.remove('loading');
        loadMoreBtn.textContent = loadMoreBtn.dataset.originalText || 'Load More';
    }
}

function createVideoCard(video) {
    const card = document.createElement('div');
    card.className = 'video-card';

    // Check if summary is cached
    const cachedSummary = getCachedSummary(video.videoUrl);
    const hasCachedSummary = cachedSummary !== null;

    card.innerHTML = `
        <div class="card-inner">
            <!-- Front face - Video info -->
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
                    <button class="action-btn save-btn" data-video-url="${video.videoUrl}">Save</button>
                </div>
            </div>
            <!-- Back face - Summary -->
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
                <button class="expand-summary-btn" data-video-url="${video.videoUrl}" data-title="${escapeHtml(video.title)}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <path d="M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7"/>
                    </svg>
                    Expand
                </button>
            </div>
        </div>
    `;

    // Summary button click - load summary and flip card
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

    // Expand button - open modal with full summary
    card.querySelector('.expand-summary-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        const summary = card.querySelector('.summary-preview').dataset.fullSummary;
        openSummaryModal(video.title, summary);
    });

    // Save button click
    card.querySelector('.save-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        saveToWatchLater(e.target, video.videoUrl);
    });

    // If summary is already cached, pre-populate the back
    if (hasCachedSummary) {
        const previewEl = card.querySelector('.summary-preview');
        previewEl.textContent = truncateText(cachedSummary, 300);
        previewEl.dataset.fullSummary = cachedSummary;
    }

    return card;
}

// Track pending summary requests
const pendingSummaries = new Map(); // videoUrl → { requestId, intervalId, card }

// Load summary and flip the card (async with polling)
async function loadSummaryAndFlip(card, videoUrl, title) {
    const summaryBtn = card.querySelector('.summary-btn');
    const previewEl = card.querySelector('.summary-preview');

    // Check local cache first
    const cachedSummary = getCachedSummary(videoUrl);
    if (cachedSummary) {
        previewEl.textContent = truncateText(cachedSummary, 300);
        previewEl.dataset.fullSummary = cachedSummary;
        card.classList.add('flipped');
        return;
    }

    // Check if already pending
    if (pendingSummaries.has(videoUrl)) {
        console.log('Summary already pending for:', videoUrl);
        return;
    }

    // Show loading state on button
    summaryBtn.disabled = true;
    summaryBtn.innerHTML = '<span class="btn-spinner"></span> Queuing...';

    try {
        // Submit request to queue
        const response = await fetch('/api/summary/request', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ videoUrl, videoTitle: title })
        });

        if (!response.ok) throw new Error('Failed to submit summary request');

        const data = await response.json();
        const { requestId, status, summary, queuePosition } = data;

        // If already completed (from server cache), show immediately
        if (status === 'COMPLETED' && summary) {
            handleSummaryComplete(card, videoUrl, summary, summaryBtn, previewEl);
            return;
        }

        // Update button to show queue position
        updateSummaryButtonStatus(summaryBtn, status, queuePosition);

        // Start polling for status
        const intervalId = setInterval(() => {
            pollSummaryStatus(requestId, card, videoUrl, summaryBtn, previewEl);
        }, 2000);

        // Track this pending request
        pendingSummaries.set(videoUrl, { requestId, intervalId, card });

        console.log(`Summary request queued: ${requestId} (position ${queuePosition})`);

    } catch (error) {
        console.error('Failed to submit summary request:', error);
        summaryBtn.disabled = false;
        summaryBtn.textContent = 'AI Summary';
        alert('Failed to request AI summary. Please try again.');
    }
}

// Poll for summary status
async function pollSummaryStatus(requestId, card, videoUrl, summaryBtn, previewEl) {
    try {
        const response = await fetch(`/api/summary/status/${requestId}`);

        if (!response.ok) {
            console.error('Failed to get summary status');
            return;
        }

        const data = await response.json();
        const { status, summary, queuePosition, error } = data;

        // Update button status
        updateSummaryButtonStatus(summaryBtn, status, queuePosition);

        if (status === 'COMPLETED') {
            // Stop polling
            stopPolling(videoUrl);
            handleSummaryComplete(card, videoUrl, summary, summaryBtn, previewEl);

        } else if (status === 'FAILED') {
            // Stop polling and show error
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

// Handle completed summary
function handleSummaryComplete(card, videoUrl, summary, summaryBtn, previewEl) {
    const finalSummary = summary || 'No summary available.';

    // Cache the summary
    if (finalSummary && finalSummary !== 'No summary available.') {
        cacheSummary(videoUrl, finalSummary);
    }

    // Update the back of the card
    previewEl.textContent = truncateText(finalSummary, 300);
    previewEl.dataset.fullSummary = finalSummary;

    // Flip the card
    card.classList.add('flipped');

    // Update button to show it has cache now
    summaryBtn.disabled = false;
    summaryBtn.classList.remove('error');
    summaryBtn.classList.add('has-cache');
    summaryBtn.textContent = 'View Summary';
}

// Update button to show current status
function updateSummaryButtonStatus(summaryBtn, status, queuePosition) {
    if (status === 'QUEUED') {
        summaryBtn.innerHTML = `<span class="btn-spinner"></span> Queue #${queuePosition}`;
    } else if (status === 'PROCESSING') {
        summaryBtn.innerHTML = '<span class="btn-spinner"></span> Processing...';
    }
}

// Stop polling for a video
function stopPolling(videoUrl) {
    const pending = pendingSummaries.get(videoUrl);
    if (pending) {
        clearInterval(pending.intervalId);
        pendingSummaries.delete(videoUrl);
    }
}

// Cleanup all pending summaries (e.g., on page unload)
function cleanupPendingSummaries() {
    for (const [videoUrl, pending] of pendingSummaries) {
        clearInterval(pending.intervalId);
    }
    pendingSummaries.clear();
}

// Cleanup on page unload
window.addEventListener('beforeunload', cleanupPendingSummaries);

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

// ==================== Summary Modal (for expanded view) ====================

function openSummaryModal(title, summary) {
    summaryTitle.textContent = title || 'Video Summary';
    summaryText.textContent = summary || 'No summary available.';
    summaryText.classList.remove('hidden');
    summaryLoading.classList.add('hidden');
    summaryModal.classList.add('active');
}

function closeModal() {
    summaryModal.classList.remove('active');
}

async function saveToWatchLater(button, videoUrl) {
    if (button.classList.contains('saved')) return;

    const originalText = button.textContent;
    button.textContent = 'Saving...';
    button.disabled = true;

    try {
        const response = await fetch('/api/save', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ videoUrl })
        });

        const data = await response.json();

        if (data.success) {
            button.textContent = 'Saved!';
            button.classList.add('saved');
        } else {
            button.textContent = originalText;
            alert('Failed to save video. Please try again.');
        }
    } catch (error) {
        console.error('Failed to save to Watch Later:', error);
        button.textContent = originalText;
        alert('Failed to save video. Please try again.');
    } finally {
        button.disabled = false;
    }
}
