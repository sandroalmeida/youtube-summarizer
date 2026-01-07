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
    const hasCachedSummary = getCachedSummary(video.videoUrl) !== null;

    card.innerHTML = `
        <div class="thumbnail-container">
            <img class="thumbnail" src="${video.thumbnailUrl || ''}" alt="${video.title || ''}" loading="lazy">
            ${video.duration ? `<span class="duration">${video.duration}</span>` : ''}
        </div>
        <div class="video-info">
            <h3 class="video-title">${video.title || 'Untitled'}</h3>
            <p class="channel-name">${video.channelName || ''}</p>
        </div>
        <div class="card-actions">
            <button class="action-btn summary-btn ${hasCachedSummary ? 'has-cache' : ''}" data-video-url="${video.videoUrl}" data-title="${escapeHtml(video.title)}">
                ${hasCachedSummary ? 'AI Summary (cached)' : 'AI Summary'}
            </button>
            <button class="action-btn save-btn" data-video-url="${video.videoUrl}">Save</button>
        </div>
    `;

    // Summary button click
    card.querySelector('.summary-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        openSummaryModal(e.target.dataset.videoUrl, e.target.dataset.title);
    });

    // Save button click
    card.querySelector('.save-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        saveToWatchLater(e.target, e.target.dataset.videoUrl);
    });

    return card;
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

// ==================== Summary Modal ====================

async function openSummaryModal(videoUrl, title) {
    summaryTitle.textContent = title || 'Video Summary';
    summaryText.classList.add('hidden');
    summaryText.textContent = '';
    summaryModal.classList.add('active');

    // Check localStorage cache first
    const cachedSummary = getCachedSummary(videoUrl);
    if (cachedSummary) {
        console.log('Summary loaded from localStorage cache');
        summaryText.textContent = cachedSummary;
        summaryText.classList.remove('hidden');
        summaryLoading.classList.add('hidden');
        return;
    }

    // Not in cache - fetch from server
    summaryLoading.classList.remove('hidden');

    try {
        const response = await fetch('/api/summary', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ videoUrl, videoTitle: title })
        });

        if (!response.ok) throw new Error('Failed to get summary');

        const data = await response.json();
        const summary = data.summary || 'No summary available.';

        // Cache the summary
        if (summary && summary !== 'No summary available.') {
            cacheSummary(videoUrl, summary);
        }

        summaryText.textContent = summary;
        summaryText.classList.remove('hidden');

    } catch (error) {
        console.error('Failed to get AI summary:', error);
        summaryText.textContent = 'Failed to get AI summary. Please try again.';
        summaryText.classList.remove('hidden');
    } finally {
        summaryLoading.classList.add('hidden');
    }
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
