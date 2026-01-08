// State
let currentTab = 'subscriptions';
let currentPage = 0;
let isLoading = false;

// Client-side video cache for instant tab switching
const videoCache = {
    subscriptions: { videos: [], pagesLoaded: 0 },
    home: { videos: [], pagesLoaded: 0 },
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

// Initialize
document.addEventListener('DOMContentLoaded', async () => {
    await loadSavedVideoUrls();
    loadAccountInfo();
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

                // Hide refresh button on saved tab (no need to refresh DB data)
                if (refreshBtn) {
                    refreshBtn.style.display = newTab === 'saved' ? 'none' : 'flex';
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

    // Modal regenerate button
    const modalRegenerateBtn = document.getElementById('modal-regenerate-btn');
    if (modalRegenerateBtn) {
        modalRegenerateBtn.addEventListener('click', regenerateFromModal);
    }
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

// Map SavedVideo entity to standard video card format
function mapSavedVideoToVideoFormat(savedVideo) {
    return {
        videoUrl: savedVideo.videoUrl,
        title: savedVideo.title,
        channelName: savedVideo.channelName,
        thumbnailUrl: savedVideo.thumbnailUrl,
        duration: savedVideo.duration,
        // Store summary directly for saved videos (pre-cache it)
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
        // Show loading state on Load More button
        loadMoreBtn.disabled = true;
        loadMoreBtn.classList.add('loading');
        loadMoreBtn.innerHTML = '<span class="btn-spinner"></span> Loading...';
    }

    try {
        // Build URL based on current tab
        let url;
        if (currentTab === 'saved') {
            // Fetch saved videos from database
            url = `/api/videos/saved?page=${currentPage}`;
        } else {
            // Fetch from YouTube scraper
            url = `/api/videos?tab=${currentTab}&page=${currentPage}`;
            if (forceRefresh) {
                url += '&forceRefresh=true';
            }
        }

        const response = await fetch(url);
        if (!response.ok) throw new Error('Failed to load videos');

        let videos = await response.json();

        // Map saved videos to standard video format
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

    // For saved videos from DB, use the embedded summary; otherwise check localStorage
    let cachedSummary = video._savedSummary || getCachedSummary(video.videoUrl);
    const hasCachedSummary = cachedSummary !== null;

    // For saved videos from DB, cache the summary in localStorage for consistency
    if (video._savedSummary && !getCachedSummary(video.videoUrl)) {
        cacheSummary(video.videoUrl, video._savedSummary);
    }

    // Check if video is already saved (from DB flag or savedVideoUrls set)
    const isVideoSaved = video._isSaved || savedVideoUrls.has(video.videoUrl);

    // Extract videoId from URL
    const videoId = extractVideoId(video.videoUrl);

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
        openSummaryModal(video.title, summary, video.videoUrl);
    });

    // Regenerate button - request fresh summary
    card.querySelector('.regenerate-btn').addEventListener('click', (e) => {
        e.stopPropagation();
        regenerateSummary(card, video.videoUrl, video.title);
    });

    // Button handlers based on current tab
    if (currentTab === 'saved') {
        // Open button - open video in new tab
        card.querySelector('.open-btn').addEventListener('click', (e) => {
            e.stopPropagation();
            window.open(video.videoUrl, '_blank');
        });

        // Delete button - delete from database with confirmation
        card.querySelector('.delete-btn').addEventListener('click', (e) => {
            e.stopPropagation();
            deleteVideoFromDatabase(e.target, video.videoUrl, video.title);
        });
    } else {
        // Save button click
        card.querySelector('.save-btn').addEventListener('click', (e) => {
            e.stopPropagation();
            saveVideoToDatabase(e.target);
        });
    }

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

// ==================== Regenerate Summary ====================

// Regenerate summary - clears cache and requests fresh summary
async function regenerateSummary(card, videoUrl, title) {
    const regenerateBtn = card.querySelector('.regenerate-btn');
    const previewEl = card.querySelector('.summary-preview');
    const summaryBtn = card.querySelector('.summary-btn');

    // Clear local cache
    clearCachedSummary(videoUrl);

    // Show loading state on regenerate button
    regenerateBtn.disabled = true;
    regenerateBtn.classList.add('loading');
    regenerateBtn.innerHTML = '<span class="btn-spinner"></span>';

    // Show loading on preview
    previewEl.textContent = 'Generating new summary...';
    previewEl.dataset.fullSummary = '';

    try {
        // Call regenerate endpoint (clears server cache too)
        const response = await fetch('/api/summary/regenerate', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ videoUrl, videoTitle: title })
        });

        if (!response.ok) throw new Error('Failed to submit regenerate request');

        const data = await response.json();
        const { requestId, status, queuePosition } = data;

        // Start polling for status
        const intervalId = setInterval(() => {
            pollRegenerateSummaryStatus(requestId, card, videoUrl, regenerateBtn, previewEl, summaryBtn);
        }, 2000);

        // Track this pending request (reuse the pendingSummaries map)
        pendingSummaries.set(videoUrl, { requestId, intervalId, card });

        // Update regenerate button to show queue status
        updateRegenerateButtonStatus(regenerateBtn, status, queuePosition);

        console.log(`Regenerate request queued: ${requestId} (position ${queuePosition})`);

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

// Poll for regenerate summary status
async function pollRegenerateSummaryStatus(requestId, card, videoUrl, regenerateBtn, previewEl, summaryBtn) {
    try {
        const response = await fetch(`/api/summary/status/${requestId}`);

        if (!response.ok) {
            console.error('Failed to get regenerate status');
            return;
        }

        const data = await response.json();
        const { status, summary, queuePosition, error } = data;

        // Update button status
        updateRegenerateButtonStatus(regenerateBtn, status, queuePosition);

        if (status === 'COMPLETED') {
            // Stop polling
            stopPolling(videoUrl);

            const finalSummary = summary || 'No summary available.';

            // Cache the new summary
            if (finalSummary && finalSummary !== 'No summary available.') {
                cacheSummary(videoUrl, finalSummary);
            }

            // Update preview
            previewEl.textContent = truncateText(finalSummary, 300);
            previewEl.dataset.fullSummary = finalSummary;

            // Reset regenerate button
            regenerateBtn.disabled = false;
            regenerateBtn.classList.remove('loading');
            regenerateBtn.innerHTML = `
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M23 4v6h-6M1 20v-6h6"/>
                    <path d="M3.51 9a9 9 0 0114.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0020.49 15"/>
                </svg>
                New
            `;

            // Update front button state
            summaryBtn.classList.add('has-cache');
            summaryBtn.textContent = 'View Summary';

            // If modal is open with this video, update it
            if (currentModalContext.videoUrl === videoUrl) {
                summaryText.textContent = finalSummary;
            }

            console.log('Summary regenerated successfully');

        } else if (status === 'FAILED') {
            // Stop polling and show error
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
            console.error('Regenerate failed:', error);
        }

    } catch (error) {
        console.error('Error polling regenerate status:', error);
    }
}

// Update regenerate button to show status
function updateRegenerateButtonStatus(btn, status, queuePosition) {
    if (status === 'QUEUED') {
        btn.innerHTML = `<span class="btn-spinner"></span> #${queuePosition}`;
    } else if (status === 'PROCESSING') {
        btn.innerHTML = '<span class="btn-spinner"></span>';
    }
}

// Regenerate from modal
async function regenerateFromModal() {
    if (!currentModalContext.videoUrl) {
        console.error('No video context for modal regeneration');
        return;
    }

    const modalRegenerateBtn = document.getElementById('modal-regenerate-btn');

    // Clear local cache
    clearCachedSummary(currentModalContext.videoUrl);

    // Show loading state
    modalRegenerateBtn.disabled = true;
    modalRegenerateBtn.classList.add('loading');
    summaryText.textContent = 'Generating new summary...';

    try {
        // Call regenerate endpoint
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

        // Poll for completion
        const checkStatus = async () => {
            const statusResponse = await fetch(`/api/summary/status/${requestId}`);
            const statusData = await statusResponse.json();

            if (statusData.status === 'COMPLETED') {
                const newSummary = statusData.summary || 'No summary available.';

                // Cache it
                if (newSummary && newSummary !== 'No summary available.') {
                    cacheSummary(currentModalContext.videoUrl, newSummary);
                }

                // Update modal
                summaryText.textContent = newSummary;
                modalRegenerateBtn.disabled = false;
                modalRegenerateBtn.classList.remove('loading');

                // Update card if we have a reference
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
                // Still processing, check again
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
        console.log('Cleared cached summary for:', videoUrl);
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

// ==================== Summary Modal (for expanded view) ====================

// Store current modal context for regeneration
let currentModalContext = { videoUrl: null, title: null, card: null };

function openSummaryModal(title, summary, videoUrl, card = null) {
    currentModalContext = { videoUrl, title, card };

    summaryTitle.textContent = title || 'Video Summary';
    summaryText.textContent = summary || 'No summary available.';
    summaryText.classList.remove('hidden');
    summaryLoading.classList.add('hidden');
    summaryModal.classList.add('active');

    // Reset regenerate button state
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

// Setup confirm modal event listeners
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

// Save video to database (requires summary to be generated first)
async function saveVideoToDatabase(button) {
    if (button.classList.contains('saved')) return;

    // Get video data from button data attributes
    const videoUrl = button.dataset.videoUrl;
    const title = button.dataset.title;
    const channelName = button.dataset.channel;
    const thumbnailUrl = button.dataset.thumbnail;
    const duration = button.dataset.duration;
    const videoId = button.dataset.videoId;

    // Check if summary exists in local cache
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
            // Add to saved set
            savedVideoUrls.add(videoUrl);
            // Invalidate saved tab cache so it reloads when user switches to it
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

// Delete video from database (for Saved tab)
async function deleteVideoFromDatabase(button, videoUrl, title) {
    // Show confirmation dialog
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
            // Remove from saved set
            savedVideoUrls.delete(videoUrl);

            // Clear from local cache
            clearCachedSummary(videoUrl);

            // Clear client-side video cache for saved tab and reload
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
