// State
let currentTab = 'subscriptions';
let currentPage = 0;
let isLoading = false;

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

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    loadAccountInfo();
    loadVideos();
    setupEventListeners();
});

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
                videoGrid.innerHTML = '';
                loadVideos();
            }
        });
    });

    // Load more
    loadMoreBtn.addEventListener('click', () => {
        currentPage++;
        loadVideos(true);
    });

    // Close modal
    document.querySelector('.close-modal').addEventListener('click', closeModal);
    summaryModal.addEventListener('click', (e) => {
        if (e.target === summaryModal) closeModal();
    });
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeModal();
    });
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

async function loadVideos(append = false) {
    if (isLoading) return;
    isLoading = true;

    if (!append) {
        loading.classList.remove('hidden');
        loadMoreBtn.classList.add('hidden');
    }

    try {
        const response = await fetch(`/api/videos?tab=${currentTab}&page=${currentPage}`);
        if (!response.ok) throw new Error('Failed to load videos');

        const videos = await response.json();

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
    }
}

function createVideoCard(video) {
    const card = document.createElement('div');
    card.className = 'video-card';
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
            <button class="action-btn summary-btn" data-video-url="${video.videoUrl}" data-title="${escapeHtml(video.title)}">AI Summary</button>
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

async function openSummaryModal(videoUrl, title) {
    summaryTitle.textContent = title || 'Video Summary';
    summaryLoading.classList.remove('hidden');
    summaryText.classList.add('hidden');
    summaryText.textContent = '';
    summaryModal.classList.add('active');

    try {
        const response = await fetch('/api/summary', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ videoUrl })
        });

        if (!response.ok) throw new Error('Failed to get summary');

        const data = await response.json();
        summaryText.textContent = data.summary || 'No summary available.';
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
