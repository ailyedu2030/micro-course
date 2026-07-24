<!--
  视频播放器 - PC + H5 沉浸式深色主题
  路由路径: /student/video/:id
  role-video 主题: 黑底 + 白字 + 强调色 #6366f1
-->
<template>
  <div class="video-player-root role-video">
    <!-- Loading State -->
    <div v-if="loading" class="player-loading">
      <div class="skeleton-video">
        <div class="skeleton-video-placeholder">
          <div class="skeleton-icon">▶</div>
        </div>
        <div class="skeleton-controls">
          <div class="skeleton-bar skeleton-bar-wide"></div>
          <div class="skeleton-controls-row">
            <div class="skeleton-btn"></div>
            <div class="skeleton-btn"></div>
            <div class="skeleton-btn"></div>
            <div class="skeleton-spacer"></div>
            <div class="skeleton-btn"></div>
            <div class="skeleton-btn"></div>
          </div>
        </div>
      </div>
      <div class="skeleton-sidebar">
        <div class="skeleton-chapter" v-for="i in 5" :key="i">
          <div class="skeleton-chapter-icon"></div>
          <div class="skeleton-chapter-text">
            <div class="skeleton-bar"></div>
            <div class="skeleton-bar skeleton-bar-short"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- Error State -->
    <div v-else-if="errorMsg" class="player-error">
      <div class="error-icon">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
      </div>
      <p class="error-title">视频加载失败</p>
      <p class="error-desc">{{ errorMsg }}</p>
      <el-button type="primary" @click="retryLoad">重新加载</el-button>
    </div>

    <!-- Player Main -->
    <template v-else>
      <!-- PC Header (>= 769px) -->
      <header class="player-header pc-header">
        <div class="header-left">
          <el-button class="back-btn" link @click="goBack" aria-label="返回">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="15 18 9 12 15 6" />
            </svg>
          </el-button>
          <span class="header-title">{{ videoData.title || '视频加载中' }}</span>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="changeSpeed">
            <span class="speed-btn">
              {{ playbackRate }}x
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6 9 12 15 18 9" />
              </svg>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <!-- P2-02: 使用统一 SPEED_OPTIONS，替换 3 处硬编码 -->
                <el-dropdown-item v-for="opt in SPEED_OPTIONS" :key="opt.value" :command="opt.value" :class="{ active: playbackRate === opt.value }">{{ opt.label }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button link @click="showChapterList = !showChapterList" aria-label="章节列表">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="8" y1="6" x2="21" y2="6" />
              <line x1="8" y1="12" x2="21" y2="12" />
              <line x1="8" y1="18" x2="21" y2="18" />
              <line x1="3" y1="6" x2="3.01" y2="6" />
              <line x1="3" y1="12" x2="3.01" y2="12" />
              <line x1="3" y1="18" x2="3.01" y2="18" />
            </svg>
          </el-button>
          <el-button link @click="toggleSettings" aria-label="设置">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3" />
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
            </svg>
          </el-button>
        </div>
      </header>

      <!-- H5 Header (<= 768px) -->
      <header class="player-header h5-header">
        <el-button class="back-btn" link @click="goBack" aria-label="返回">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </el-button>
        <span class="header-title">{{ videoData.title || '视频' }}</span>
        <div class="h5-header-right">
          <el-dropdown trigger="click" @command="changeSpeed">
            <span class="speed-btn">{{ playbackRate }}x</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="opt in SPEED_OPTIONS" :key="opt.value" :command="opt.value" :class="{ active: playbackRate === opt.value }">{{ opt.label }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- Main Content -->
      <div class="player-body">
        <!-- PC Left Content -->
        <div class="player-main pc-main">
          <!-- Video Container -->
          <div
            ref="videoContainerRef"
            class="video-container"
            :class="{ 'controls-visible': controlsVisible || !isPlaying }"
            @mousemove="showControls"
            @mouseleave="hideControlsDelayed"
            @touchend="handleTouchEnd"
            @touchmove="handleTouchMove"
            @touchstart="handleTouchStart"
          >
            <video
              ref="videoRef"
              class="video-element"
              :poster="videoData.thumbnail"
              @canplay="onCanPlay"
              @timeupdate="onTimeUpdate"
              @ended="onEnded"
              @error="onVideoError"
              @waiting="onBufferingStart"
              @playing="onBufferingEnd"
              @progress="onProgress"
              @dblclick="togglePlay"
            ></video>

            <!-- Buffering Spinner -->
            <div v-if="isBuffering" class="video-buffering">
              <div class="buffering-spinner"></div>
            </div>

            <!-- P1-1: HLS Fatal Error Retry Overlay -->
            <div v-if="hlsFatal" class="hls-error-fallback">
              <div class="hls-error-icon">
                <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <circle cx="12" cy="12" r="10" />
                  <line x1="12" y1="8" x2="12" y2="12" />
                  <line x1="12" y1="16" x2="12.01" y2="16" />
                </svg>
              </div>
              <p class="hls-error-text">视频加载失败</p>
              <el-button type="primary" size="default" @click="retryHls">重试</el-button>
            </div>

            <!-- Gesture Indicators -->
            <transition name="gesture-fade">
              <div v-if="volumeIndicatorVisible" class="gesture-indicator volume-indicator" :style="{ left: gestureIndicatorX + 'px', top: gestureIndicatorY + 'px' }">
                <div class="gi-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
                    <path v-if="volumeIndicatorValue > 0" d="M15.54 8.46a5 5 0 0 1 0 7.07" />
                  </svg>
                </div>
                <div class="gi-bar">
                  <div class="gi-bar-fill" :style="{ height: volumeIndicatorValue + '%' }"></div>
                </div>
                <span class="gi-value">{{ volumeIndicatorValue }}</span>
              </div>
            </transition>

            <transition name="gesture-fade">
              <div v-if="brightnessIndicatorVisible" class="gesture-indicator brightness-indicator" :style="{ left: gestureIndicatorX + 'px', top: gestureIndicatorY + 'px' }">
                <div class="gi-icon">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="5" />
                    <line x1="12" y1="1" x2="12" y2="3" />
                    <line x1="12" y1="21" x2="12" y2="23" />
                    <line x1="4.22" y1="4.22" x2="5.64" y2="5.64" />
                    <line x1="18.36" y1="18.36" x2="19.78" y2="19.78" />
                    <line x1="1" y1="12" x2="3" y2="12" />
                    <line x1="21" y1="12" x2="23" y2="12" />
                    <line x1="4.22" y1="19.78" x2="5.64" y2="18.36" />
                    <line x1="18.36" y1="5.64" x2="19.78" y2="4.22" />
                  </svg>
                </div>
                <div class="gi-bar">
                  <div class="gi-bar-fill" :style="{ height: brightnessIndicatorValue + '%' }"></div>
                </div>
                <span class="gi-value">{{ brightnessIndicatorValue }}</span>
              </div>
            </transition>

            <!-- Double Tap Seek Indicator -->
            <transition name="seek-indicator-fade">
              <div v-if="showSeekIndicator" class="seek-indicator">
                <svg v-if="seekIndicatorDir === 'backward'" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="11 19 2 12 11 5 11 19" />
                  <polygon points="22 19 13 12 22 5 22 19" />
                </svg>
                <svg v-else width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <polygon points="13 19 22 12 13 5 13 19" />
                  <polygon points="2 19 11 12 2 5 2 19" />
                </svg>
                <span>{{ seekIndicatorSeconds }}s</span>
              </div>
            </transition>

            <!-- Center Play Button (when paused) -->
            <div v-if="!isPlaying && !isBuffering && !loading" class="center-play-btn" role="button" tabindex="0" aria-label="播放视频" @click="togglePlay" @keydown.enter="togglePlay" @keydown.space.prevent="togglePlay">
              <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                <polygon points="5 3 19 12 5 21 5 3" />
              </svg>
            </div>

            <!-- Top Overlay -->
            <div class="video-top-overlay">
              <div class="video-title-overlay">{{ videoData.title }}</div>
            </div>

            <!-- Learning Objectives Overlay (auto-hide after 3s) -->
            <transition name="obj-fade">
              <div v-if="showObjectives" class="learning-objectives-overlay">
                <div class="obj-icon">
                  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="10" />
                    <line x1="12" y1="8" x2="12" y2="12" />
                    <line x1="12" y1="16" x2="12.01" y2="16" />
                  </svg>
                </div>
                <div class="obj-content">
                  <div class="obj-label">本节目标</div>
                  <div class="obj-text">{{ currentChapter?.description || '掌握核心概念' }}</div>
                </div>
              </div>
            </transition>

            <!-- Watermark Overlay (P1-13: 前端提示性水印 - 用户ID+时间戳) -->
            <div class="video-watermark-overlay">
              <span class="watermark-text">{{ watermarkText }}</span>
            </div>

            <!-- Custom Controls -->
            <div class="video-controls" :class="{ visible: controlsVisible || !isPlaying }">
              <!-- Progress Bar -->
              <div class="progress-track" role="slider" tabindex="0" :aria-label="`视频进度条 当前 ${Math.round(progressPercent)}%`" :aria-valuemin="0" :aria-valuemax="100" :aria-valuenow="Math.round(progressPercent)" @click="seekVideo" ref="progressTrack" @keydown.left.prevent="seekRelative(-5)" @keydown.right.prevent="seekRelative(5)">
                <div class="progress-buffer" :style="{ width: bufferedPercent + '%' }"></div>
                <div class="progress-played" :style="{ width: progressPercent + '%' }">
                  <div class="progress-thumb"></div>
                </div>
              </div>

              <!-- Control Buttons -->
              <div class="controls-row">
                <div class="controls-left">
                  <!-- Play/Pause -->
                  <button class="ctrl-btn" @click="togglePlay" :aria-label="isPlaying ? '暂停' : '播放'">
                    <svg v-if="isPlaying" width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                      <rect x="6" y="4" width="4" height="16" />
                      <rect x="14" y="4" width="4" height="16" />
                    </svg>
                    <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                      <polygon points="5 3 19 12 5 21 5 3" />
                    </svg>
                  </button>

                  <!-- Skip Backward -->
                  <button class="ctrl-btn" @click="skipBackward" aria-label="快退10秒">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polygon points="11 19 2 12 11 5 11 19" />
                      <polygon points="22 19 13 12 22 5 22 19" />
                    </svg>
                  </button>

                  <!-- Skip Forward -->
                  <button class="ctrl-btn" @click="skipForward" aria-label="快进10秒">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polygon points="13 19 22 12 13 5 13 19" />
                      <polygon points="2 19 11 12 2 5 2 19" />
                    </svg>
                  </button>

                  <!-- Volume -->
                  <div class="volume-control">
                    <button class="ctrl-btn" @click="toggleMute" :aria-label="isMuted ? '取消静音' : '静音'">
                      <svg v-if="isMuted || volume === 0" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
                        <line x1="23" y1="9" x2="17" y2="15" />
                        <line x1="17" y1="9" x2="23" y2="15" />
                      </svg>
                      <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
                        <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
                        <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
                      </svg>
                    </button>
                    <el-slider
                      v-model="volumePercent"
                      :show-tooltip="false"
                      class="volume-slider"
                      @input="changeVolume"
                    />
                  </div>

                  <!-- Time -->
                  <span class="time-display">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
                </div>

                <div class="controls-right">
                  <!-- Speed -->
                  <el-dropdown trigger="click" @command="changeSpeed">
                    <button class="ctrl-btn speed-ctrl-btn">
                      {{ playbackRate }}x
                    </button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item v-for="opt in SPEED_OPTIONS" :key="opt.value" :command="opt.value" :class="{ active: playbackRate === opt.value }">{{ opt.label }}</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>

                  <!-- Subtitles -->
                  <button v-if="videoData.subtitleUrl" class="ctrl-btn" :class="{ active: subtitlesEnabled }" @click="toggleSubtitles" aria-label="字幕">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="2" y="6" width="20" height="12" rx="2" />
                      <line x1="6" y1="12" x2="18" y2="12" />
                      <line x1="8" y1="16" x2="16" y2="16" />
                    </svg>
                  </button>

                  <!-- Picture-in-Picture -->
                  <button v-if="isPipSupported" class="ctrl-btn" :class="{ active: isPip }" @click="togglePictureInPicture" :aria-label="isPip ? '退出画中画' : '画中画'">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
                      <rect x="12" y="9" width="8" height="6" rx="1" ry="1" :fill="isPip ? 'currentColor' : 'none'" />
                    </svg>
                  </button>

                  <!-- Fullscreen -->
                  <button class="ctrl-btn" @click="toggleFullscreen" :aria-label="isFullscreen ? '退出全屏' : '全屏'">
                    <svg v-if="!isFullscreen" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="15 3 21 3 21 9" />
                      <polyline points="9 21 3 21 3 15" />
                      <line x1="21" y1="3" x2="14" y2="10" />
                      <line x1="3" y1="21" x2="10" y2="14" />
                    </svg>
                    <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="4 14 10 14 10 20" />
                      <polyline points="20 10 14 10 14 4" />
                      <line x1="14" y1="10" x2="21" y2="3" />
                      <line x1="3" y1="21" x2="10" y2="14" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>

            <!-- Subtitles -->
            <div v-if="subtitlesEnabled && currentSubtitle" class="video-subtitles">
              {{ currentSubtitle }}
            </div>
          </div>

          <!-- Chapter Progress Chips (PC) -->
          <div class="chapter-chips pc-chips">
            <div
              v-for="(chapter, index) in chapters"
              :key="chapter.id"
              class="chapter-chip"
              :class="{
                'is-active': currentChapterIndex === index,
                'is-completed': chapter.isCompleted
              }"
              @click="switchChapter(chapter.id)"
            >
              <span class="chip-index">{{ index + 1 }}</span>
              <span class="chip-title">{{ chapter.title }}</span>
              <svg v-if="chapter.isCompleted" class="chip-check" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="20 6 9 17 4 12" />
              </svg>
            </div>
          </div>

          <!-- Video Info Card -->
          <div class="video-info-card pc-info">
            <div class="info-row">
              <span class="info-label">课程：</span>
              <span class="info-value">{{ videoData.courseTitle || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">章节：</span>
              <span class="info-value">{{ currentChapter?.title || '-' }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">时长：</span>
              <span class="info-value">{{ formatTime(duration) }}</span>
            </div>
            <div v-if="lastPosition > 0" class="info-row">
              <span class="info-label">上次进度：</span>
              <span class="info-value">{{ formatTime(lastPosition) }}</span>
            </div>
          </div>
        </div>

        <!-- Right Sidebar (PC >= 769px) -->
        <aside class="player-sidebar pc-sidebar">
          <el-tabs v-model="activeTab" class="sidebar-tabs">
            <el-tab-pane label="章节" name="chapters">
              <div class="tab-content chapters-tab">
                <div v-if="chapters.length === 0" class="tab-empty">暂无章节</div>
                <div
                  v-for="(chapter, index) in chapters"
                  :key="chapter.id"
                  :ref="el => setChapterItemRef(el, index)"
                  class="chapter-item"
                  :class="{
                    'is-active': currentChapterIndex === index,
                    'is-completed': chapter.isCompleted
                  }"
                  @click="switchChapter(chapter.id)"
                >
                  <div class="chapter-index">{{ index + 1 }}</div>
                  <div class="chapter-info">
                    <div class="chapter-title">{{ chapter.title }}</div>
                    <div class="chapter-duration">{{ formatTime(chapter.duration || 0) }}</div>
                  </div>
                  <svg v-if="chapter.isCompleted" class="chapter-check" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="笔记" name="notes">
              <div class="tab-content notes-tab">
                <div class="note-input-row">
                  <span class="note-time-btn" role="button" tabindex="0" aria-label="在当前时间点插入笔记" @click="insertNoteAtCurrentTime" @keydown.enter="insertNoteAtCurrentTime" @keydown.space.prevent="insertNoteAtCurrentTime">{{ formatTime(currentTime) }}</span>
                  <el-input
                    v-model="noteText"
                    placeholder="添加笔记..."
                    class="note-input"
                    @keyup.enter="addNote"
                  />
                  <el-button type="primary" size="small" @click="addNote">添加</el-button>
                </div>
                <div v-if="notes.length === 0" class="tab-empty">暂无笔记</div>
                <div
                  v-for="note in notes"
                  :key="note.id"
                  class="note-item"
                  @mouseenter="highlightTime(note.time)"
                  @mouseleave="highlightTime(null)"
                >
                  <span class="note-time" role="button" tabindex="0" :aria-label="`跳转到 ${formatTime(note.time)}`" @click="seekToTime(note.time)" @keydown.enter="seekToTime(note.time)" @keydown.space.prevent="seekToTime(note.time)">{{ formatTime(note.time) }}</span>
                  <span class="note-content">{{ note.content }}</span>
                  <el-button link size="small" @click="deleteNote(note.id)">删除</el-button>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="讨论" name="discussions">
              <div class="tab-content discussions-tab">
                <div v-if="discussions.length === 0" class="tab-empty">暂无讨论</div>
                <div
                  v-for="post in discussions"
                  :key="post.id"
                  class="discussion-item"
                >
                  <div class="discussion-header">
                    <span class="discussion-author">{{ post.authorName }}</span>
                    <span class="discussion-time">{{ formatDateTime(post.createdAt) }}</span>
                  </div>
                  <div class="discussion-title">{{ post.title }}</div>
                  <div class="discussion-preview">{{ post.content }}</div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </aside>

        <!-- H5 Bottom Tabs (<= 768px) -->
        <div class="h5-bottom-tabs">
          <el-tabs v-model="activeTab" class="h5-tabs" swipeable>
            <el-tab-pane label="章节" name="chapters">
              <div class="tab-content chapters-tab h5-chapters">
                <div v-if="chapters.length === 0" class="tab-empty">暂无章节</div>
                <div
                  v-for="(chapter, index) in chapters"
                  :key="chapter.id"
                  class="chapter-item h5-chapter-item"
                  :class="{
                    'is-active': currentChapterIndex === index,
                    'is-completed': chapter.isCompleted
                  }"
                  @click="switchChapter(chapter.id)"
                >
                  <div class="chapter-index">{{ index + 1 }}</div>
                  <div class="chapter-info">
                    <div class="chapter-title">{{ chapter.title }}</div>
                    <div class="chapter-duration">{{ formatTime(chapter.duration || 0) }}</div>
                  </div>
                  <svg v-if="chapter.isCompleted" class="chapter-check" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <polyline points="20 6 9 17 4 12" />
                  </svg>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="笔记" name="notes">
              <div class="tab-content notes-tab h5-notes">
                <div class="note-input-row">
                  <span class="note-time-btn" role="button" tabindex="0" aria-label="在当前时间点插入笔记" @click="insertNoteAtCurrentTime" @keydown.enter="insertNoteAtCurrentTime" @keydown.space.prevent="insertNoteAtCurrentTime">{{ formatTime(currentTime) }}</span>
                  <el-input
                    v-model="noteText"
                    placeholder="添加笔记..."
                    class="note-input"
                    @keyup.enter="addNote"
                  />
                  <el-button type="primary" size="small" @click="addNote">添加</el-button>
                </div>
                <div v-if="notes.length === 0" class="tab-empty">暂无笔记</div>
                <div
                  v-for="note in notes"
                  :key="note.id"
                  class="note-item h5-note-item"
                >
                  <span class="note-time" role="button" tabindex="0" :aria-label="`跳转到 ${formatTime(note.time)}`" @click="seekToTime(note.time)" @keydown.enter="seekToTime(note.time)" @keydown.space.prevent="seekToTime(note.time)">{{ formatTime(note.time) }}</span>
                  <span class="note-content">{{ note.content }}</span>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="讨论" name="discussions">
              <div class="tab-content discussions-tab h5-discussions">
                <div v-if="discussions.length === 0" class="tab-empty">暂无讨论</div>
                <div
                  v-for="post in discussions"
                  :key="post.id"
                  class="discussion-item h5-discussion-item"
                >
                  <div class="discussion-header">
                    <span class="discussion-author">{{ post.authorName }}</span>
                    <span class="discussion-time">{{ formatDateTime(post.createdAt) }}</span>
                  </div>
                  <div class="discussion-title">{{ post.title }}</div>
                  <div class="discussion-preview">{{ post.content }}</div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </template>

    <!-- Speed Toast -->
    <transition name="toast-fade">
      <div v-if="speedToastVisible" class="speed-toast">{{ playbackRate }}x</div>
    </transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
// P2-02: 统一倍速选项配置，替换 3 处硬编码
import { SPEED_OPTIONS } from '@/composables/usePlaybackSpeed'
import { useLearningProgressReporter } from '@/composables/useLearningProgressReporter'
import { useLearningProgressHeartbeat } from '@/composables/useLearningProgressHeartbeat'
import { useVideoBufferingWatchdog } from '@/composables/useVideoBufferingWatchdog'
import { useVideoLearningData } from '@/composables/useVideoLearningData'
import { useVideoLoadOrchestrator } from '@/composables/useVideoLoadOrchestrator'
import { useVideoLocalState } from '@/composables/useVideoLocalState'
import { useVideoPlaybackControls } from '@/composables/useVideoPlaybackControls'
import { useVideoKeyboardShortcuts } from '@/composables/useVideoKeyboardShortcuts'
import { useVideoSourceLifecycle } from '@/composables/useVideoSourceLifecycle'
import { useVideoTouchGestures } from '@/composables/useVideoTouchGestures'
import { useVideoUiState } from '@/composables/useVideoUiState'
import { getLearningProgress, updateLearningProgress, createLearningProgress } from '@/api/learning-progress'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// DOM refs
const videoRef = ref(null)
const videoContainerRef = ref(null)
const progressTrack = ref(null)

// Route params
const videoId = computed(() => route.params.videoId || route.query.videoId)
const courseId = computed(() => route.params.id || route.query.courseId)
const chapterId = computed(() => route.query.chapterId)

// State
const loading = ref(true)
const errorMsg = ref('')
const videoData = ref({})
const chapters = ref([])
const discussions = ref([])
const activeTab = ref('chapters')
const showChapterList = ref(false)

const isPipSupported = ref(false)
const currentSubtitle = ref('')
const currentChapterIndex = ref(0)
const currentChapter = computed(() => chapters.value[currentChapterIndex.value])

// Chapter item refs for smooth scroll
const chapterItemRefs = ref({})
const setChapterItemRef = (el, index) => {
  if (el) {
    chapterItemRefs.value[index] = el
  }
}
const scrollToActiveChapter = () => {
  nextTick(() => {
    const el = chapterItemRefs.value[currentChapterIndex.value]
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    }
  })
}

// Progress reporting
const progressId = ref(null)
let lastReportedProgress = 0
let lastFailedProgress = null // P0-L01: track failed progress for retry
let isComponentUnmounted = false // P1-2: prevent state updates after unmount

const getCurrentProgressSnapshot = () => {
  const video = videoRef.value
  if (!video || !video.duration) {
    return null
  }
  const current = video.currentTime
  return {
    current,
    progressPercentVal: (current / video.duration) * 100
  }
}

const {
  persistProgress: persistVideoProgress,
  resetProgressReporter: resetVideoProgressReporter
} = useLearningProgressReporter({
  getDedupKey: () => videoId.value ? `progress_dedup_video_${videoId.value}` : '',
  shouldPersist: ({ force }) => {
    if (!force && isComponentUnmounted) return false
    const video = videoRef.value
    if (!video || !video.duration) return false
    if (!force && video.paused) return false
    return true
  },
  getProgressRecord: () => progressId.value ? { id: progressId.value } : null,
  setProgressRecord: (record) => {
    if (record?.id) {
      progressId.value = record.id
    }
  },
  createPayload: () => {
    const snapshot = getCurrentProgressSnapshot()
    return {
      userId: userStore.userInfo?.id,
      courseId: courseId.value,
      chapterId: chapterId.value,
      videoPosition: Math.floor(snapshot?.current || 0),
      videoProgress: Math.round(snapshot?.progressPercentVal || 0)
    }
  },
  updatePayload: () => {
    const snapshot = getCurrentProgressSnapshot()
    return {
      videoPosition: Math.floor(snapshot?.current || 0),
      videoProgress: Math.round(snapshot?.progressPercentVal || 0)
    }
  },
  createProgress: createLearningProgress,
  updateProgress: updateLearningProgress,
  findExistingProgress: async () => {
    const res = await getLearningProgress({
      courseId: courseId.value,
      chapterId: chapterId.value
    })
    const rawData = res.data || []
    if (Array.isArray(rawData)) {
      return rawData.find(p => Number(p.chapterId) === Number(chapterId.value))
    }
    if (rawData && typeof rawData === 'object' && rawData.id &&
      Number(rawData.chapterId) === Number(chapterId.value)) {
      return rawData
    }
    return null
  },
  onPersisted: () => {
    const snapshot = getCurrentProgressSnapshot()
    lastReportedProgress = snapshot?.progressPercentVal || 0
    lastFailedProgress = null
    if (snapshot) {
      saveLocalPosition(snapshot.current)
    }
  },
  onError: ({ error }) => {
    const snapshot = getCurrentProgressSnapshot()
    lastFailedProgress = snapshot?.progressPercentVal ?? lastFailedProgress
    if (!sessionStorage.getItem(`progress_error_${videoId.value}`)) {
      sessionStorage.setItem(`progress_error_${videoId.value}`, '1')
      ElMessage.warning('进度上报失败,请检查网络')
    }
    console.warn('[进度上报]', error)
  }
})

const {
  isMobile,
  showObjectives,
  syncViewportMode,
  handleResize,
  showObjectivesOverlay
} = useVideoUiState()

// P1-13: 前端提示性水印（用户ID+时间戳）
const watermarkText = computed(() => {
  const uid = userStore.userInfo?.id || 'unknown'
  const now = new Date()
  const ts = `${now.getFullYear()}${String(now.getMonth()+1).padStart(2,'0')}${String(now.getDate()).padStart(2,'0')} ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`
  return `用户 ${uid} · ${ts}`
})

// Computed
const progressPercent = computed(() => {
  if (!duration.value) return 0
  return (currentTime.value / duration.value) * 100
})

// Utils
const formatTime = (seconds) => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0) {
    return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

const formatDateTime = (isoString) => {
  if (!isoString) return ''
  const d = new Date(isoString)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const {
  isPlaying,
  isMuted,
  isFullscreen,
  isPip,
  subtitlesEnabled,
  playbackRate,
  volumePercent,
  currentTime,
  duration,
  bufferedPercent,
  controlsVisible,
  speedToastVisible,
  togglePlay,
  skipBackward,
  skipForward,
  seekRelative,
  toggleMute,
  changeVolume,
  changeSpeed,
  toggleSubtitles,
  toggleFullscreen,
  togglePictureInPicture,
  handlePipEnter,
  handlePipLeave,
  seekVideo,
  showControls,
  hideControlsDelayed,
  onCanPlay,
  onTimeUpdate,
  onProgress,
  handleFullscreenChange
} = useVideoPlaybackControls({
  videoRef,
  videoContainerRef,
  progressTrackRef: progressTrack,
  getLastPosition: () => lastPosition.value
})

const volume = computed(() => volumePercent.value / 100)

const {
  handleKeydown
} = useVideoKeyboardShortcuts({
  videoRef,
  volumePercent,
  togglePlay,
  skipBackward,
  skipForward,
  changeVolume,
  toggleFullscreen,
  toggleMute,
  showControls
})

const {
  volumeIndicatorVisible,
  brightnessIndicatorVisible,
  volumeIndicatorValue,
  brightnessIndicatorValue,
  gestureIndicatorX,
  gestureIndicatorY,
  showSeekIndicator,
  seekIndicatorDir,
  seekIndicatorSeconds,
  handleTouchStart,
  handleTouchMove,
  handleTouchEnd
} = useVideoTouchGestures({
  isMobile,
  videoRef,
  changeVolume,
  skipBackward,
  skipForward
})

const {
  isBuffering,
  onBufferingStart,
  onBufferingEnd,
  stopWatchdog: stopBufferingWatchdog
} = useVideoBufferingWatchdog({
  showWarning: (options) => {
    ElMessage.warning(options)
  },
  showRetryConfirm: ({ message, title, options }) => ElMessageBox.confirm(message, title, options),
  onRetry: () => retryHls()
})

const {
  hlsFatal,
  initPlayer,
  retryLoad,
  retryHls,
  destroyPlayer
} = useVideoSourceLifecycle({
  videoRef,
  isPipSupported,
  handlePipEnter,
  handlePipLeave,
  getVideoUrl: () => videoData.value.hlsUrl || videoData.value.url,
  loadVideo: () => loadVideo(),
  setErrorMessage: (message) => {
    errorMsg.value = message
  },
  scheduleRetryInit: () => nextTick()
})

const {
  lastPosition,
  notes,
  noteText,
  saveLocalPosition,
  loadLocalPosition,
  loadNotesFromStorage,
  addNote: addStoredNote,
  deleteNote: deleteStoredNote,
  insertNoteAtCurrentTime: insertStoredNoteAtCurrentTime
} = useVideoLocalState({
  videoId,
  currentTime,
  formatTime,
  confirmDelete: async () => {
    await ElMessageBox.confirm('确定删除此笔记?', '确认删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
  },
  onStorageError: ({ type, error }) => {
    if (type === 'notes_save') {
      ElMessage.warning('笔记保存失败')
    }
    if (type === 'position_load') {
      console.warn('[VideoPlayer] loadLocalPosition 恢复播放位置失败', error)
    } else if (type === 'notes_load') {
      console.warn('[VideoPlayer] loadNotesFromStorage 加载笔记失败', error)
    } else if (type === 'notes_save') {
      console.warn('[VideoPlayer] saveNotesToStorage 保存笔记失败', error)
    } else if (type === 'position_save') {
      console.warn('[VideoPlayer] saveLocalPosition 保存播放位置失败', error)
    }
  }
})

const {
  loadChapters,
  loadProgress,
  loadDiscussions,
  switchChapter
} = useVideoLearningData({
  courseId,
  chapterId,
  userId: computed(() => userStore.userInfo?.id),
  chaptersRef: chapters,
  discussionsRef: discussions,
  currentChapterIndexRef: currentChapterIndex,
  progressIdRef: progressId,
  lastPositionRef: lastPosition,
  route,
  router,
  reportProgress: () => reportProgress(),
  reloadVideo: () => loadVideo(),
  isComponentUnmounted: () => isComponentUnmounted,
  onActiveChapterChange: () => {
    scrollToActiveChapter()
  },
  onChaptersError: (error) => {
    console.warn('[VideoPlayer] loadChapters 加载章节失败', error)
    ElMessage.warning('章节列表加载失败，部分功能不可用')
  },
  onProgressError: (error) => {
    console.warn('[VideoPlayer] loadProgress 加载学习进度失败', error)
    ElMessage.warning('学习进度加载失败，进度记忆不可用')
  },
  onDiscussionsError: (error) => {
    console.warn('[VideoPlayer] loadDiscussions 加载讨论失败', error)
  }
})

const {
  loadVideo
} = useVideoLoadOrchestrator({
  loadingRef: loading,
  errorMsgRef: errorMsg,
  videoDataRef: videoData,
  videoId,
  nextTickFn: nextTick,
  initPlayer,
  loadChapters,
  loadProgress,
  loadDiscussions,
  loadLocalPosition,
  loadNotesFromStorage,
  showObjectivesOverlay,
  isComponentUnmounted: () => isComponentUnmounted,
  onLoadError: (error) => {
    console.warn('[VideoPlayer] loadVideo 加载视频失败', error)
  }
})

const reportProgress = async (force = false) => {
  const snapshot = getCurrentProgressSnapshot()
  if (!snapshot) return
  const { progressPercentVal } = snapshot
  // P0-L01: 差异不足 1% 且无待重试的失败记录 → 跳过；失败重试不受此限
  if (!force && Math.abs(progressPercentVal - lastReportedProgress) < 1 && lastFailedProgress === null) return
  await persistVideoProgress({ force })
}

const {
  startHeartbeat: startVideoProgressHeartbeat,
  stopHeartbeat: stopVideoProgressHeartbeat
} = useLearningProgressHeartbeat({
  onInterval: reportProgress,
  onBeforeUnmountPersist: async () => {
    const video = videoRef.value

    if (video) {
      saveLocalPosition(video.currentTime)
    }

    try {
      await reportProgress(true)
    } catch (e) {
      console.warn('[VideoPlayer] final progress report failed:', e)
    }
  }
})

const addNote = () => {
  if (!addStoredNote()) return
  ElMessage.success('笔记已添加')
}

const deleteNote = async (id) => {
  const deleted = await deleteStoredNote(id)
  if (deleted) {
    ElMessage.success('笔记已删除')
  }
}

// P1-3: Insert timestamp prefix at current time
const insertNoteAtCurrentTime = () => {
  insertStoredNoteAtCurrentTime()
}

const highlightTime = () => {
  // Could emit event to highlight in video if needed
}

const seekToTime = (time) => {
  const video = videoRef.value
  if (video) {
    video.currentTime = time
  }
}

const onEnded = async () => {
  isPlaying.value = false
  if (chapters.value[currentChapterIndex.value]) {
    chapters.value[currentChapterIndex.value].isCompleted = true
  }
  await reportProgress()
  const chapter = chapters.value[currentChapterIndex.value]
  if (chapter && chapter.exerciseCount > 0) {
    ElMessageBox.confirm(
      `「${chapter.title}」的视频已看完，是否开始本节练习？`,
      '视频播放完成',
      { confirmButtonText: '开始练习', cancelButtonText: '继续看下一节', type: 'success' }
    ).then(() => {
      router.push(`/student/chapters/${chapter.id}/exercises`)
    }).catch(() => {})
  } else {
    ElMessage.success('视频播放完成')
  }
}

const onVideoError = () => {
  errorMsg.value = '视频播放出错，请尝试刷新页面'
}

// Navigation
const goBack = () => {
  router.back()
}

const toggleSettings = () => {
  // Could show settings panel
}

onMounted(async () => {
  syncViewportMode()
  isPipSupported.value = document.pictureInPictureEnabled && typeof HTMLVideoElement.prototype.requestPictureInPicture === 'function'
  resetVideoProgressReporter()
  await nextTick()
  loadVideo()
  // P1-1: Progress reporting controlled by play state via watch (no immediate start)
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  window.addEventListener('resize', handleResize)
  // Scroll to initial active chapter
  scrollToActiveChapter()
})

// P1-1: Watch isPlaying to start/stop progress timer
watch(isPlaying, (playing) => {
  if (playing) {
    startVideoProgressHeartbeat()
  } else {
    stopVideoProgressHeartbeat()
  }
})

onBeforeUnmount(() => {
  // P1-C #7: 心跳 composable 已先完成本地保存与强制上报，这里再进入资源卸载阶段
  isComponentUnmounted = true

  // 4. 清理播放器资源
  destroyPlayer()
  // P1-3: 清理缓冲 watchdog,避免内存泄漏
  stopBufferingWatchdog()
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
/* ============================================================
   Video Player - Immersive Dark Theme
   role-video: #000 bg, #fff text, #6366f1 accent
   ============================================================ */

.video-player-root {
  --vp-bg: #000000;
  --vp-surface: #1a1a1a;
  --vp-border: #333333;
  --vp-text: #ffffff;
  --vp-text-secondary: #999999;
  --vp-accent: #6366f1;
  --vp-accent-light: #818cf8;
  --vp-progress-bg: #404040;
  --vp-progress-buffered: #666666;

  background: var(--vp-bg);
  color: var(--vp-text);
  min-height: 100dvh;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* Loading — Skeleton Screen */
.player-loading {
  flex: 1;
  display: flex;
  gap: 12px;
  padding: 12px;
}

.skeleton-video { flex: 1; display: flex; flex-direction: column; gap: 8px; }

.skeleton-video-placeholder {
  flex: 1; background: var(--vp-surface); border-radius: 8px;
  display: flex; align-items: center; justify-content: center; min-height: 400px;
}

.skeleton-icon { font-size: 48px; color: var(--vp-border); opacity: 0.5; }

.skeleton-controls { display: flex; flex-direction: column; gap: 8px; padding: 12px; }

.skeleton-controls-row { display: flex; gap: 12px; align-items: center; }

.skeleton-bar {
  height: 12px; background: var(--vp-surface); border-radius: 6px;
  position: relative; overflow: hidden;
}
.skeleton-bar::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.08), transparent);
  animation: skeleton-shimmer 1.5s infinite;
}
.skeleton-bar-wide { width: 100%; }
.skeleton-bar-short { width: 60%; }

.skeleton-btn {
  width: 32px; height: 32px; border-radius: 50%;
  background: var(--vp-surface); position: relative; overflow: hidden;
}
.skeleton-btn::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.08), transparent);
  animation: skeleton-shimmer 1.5s infinite;
}
.skeleton-spacer { flex: 1; }

.skeleton-sidebar { width: 280px; display: flex; flex-direction: column; gap: 8px; }

.skeleton-chapter {
  display: flex; gap: 10px; padding: 10px;
  background: var(--vp-surface); border-radius: 6px;
}
.skeleton-chapter-icon {
  width: 40px; height: 40px; border-radius: 4px;
  background: var(--vp-border); flex-shrink: 0; position: relative; overflow: hidden;
}
.skeleton-chapter-icon::after {
  content: ''; position: absolute; inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.08), transparent);
  animation: skeleton-shimmer 1.5s infinite;
}
.skeleton-chapter-text { flex: 1; display: flex; flex-direction: column; gap: 6px; justify-content: center; }
.skeleton-chapter-text .skeleton-bar { height: 10px; }

@keyframes skeleton-shimmer {
  0% { transform: translateX(-100%); }
  100% { transform: translateX(100%); }
}

/* Error */
.player-error {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: var(--space-6);
  text-align: center;
}

.error-icon {
  color: var(--vp-text-secondary);
}

.error-title {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  margin: 0;
}

.error-desc {
  color: var(--vp-text-secondary);
  font-size: var(--text-base);
  margin: 0 0 var(--space-4);
}

/* Header */
.player-header {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-4);
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--vp-border);
  position: sticky;
  top: 0;
  z-index: 100;
}

.pc-header {
  display: flex;
}

.h5-header {
  display: none;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex: 1;
  min-width: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.header-title {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.back-btn {
  color: var(--vp-text);
  padding: var(--space-1);
}

.speed-btn {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--vp-text);
  font-size: var(--text-sm);
  cursor: pointer;
  padding: var(--space-1) var(--space-2);
  border-radius: var(--radius-sm);
  background: var(--vp-surface);
  transition: background 0.2s;
}

.speed-btn:hover {
  background: var(--vp-border);
}

.h5-header-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

/* Player Body */
.player-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* PC Main */
.player-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-6);
  gap: var(--space-5);
  overflow-y: auto;
}

.pc-main {
  max-width: calc(100% - 360px);
}

/* Video Container */
.video-container {
  position: relative;
  width: 100%;
  max-width: 1280px;
  aspect-ratio: 16 / 9;
  background: var(--vp-bg);
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: pointer;
}

.video-element {
  width: 100%;
  height: 100%;
  display: block;
}

/* Buffering */
.video-buffering {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}

/* P1-1: HLS Fatal Error Fallback Overlay */
.hls-error-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  background: rgba(0, 0, 0, 0.85);
  z-index: 10;
}

.hls-error-icon {
  color: var(--el-color-danger);
  opacity: 0.8;
}

.hls-error-text {
  font-size: var(--text-base);
  color: var(--vp-text);
  margin: 0;
}

.buffering-spinner {
  width: 48px;
  height: 48px;
  border: 3px solid rgba(255, 255, 255, 0.2);
  border-top-color: var(--vp-accent);
  border-radius: var(--radius-circle);
  animation: spin 1s linear infinite;
}

/* Center Play */
.center-play-btn {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.3);
  color: var(--el-color-white);
  transition: background 0.2s;
}

.center-play-btn:hover {
  background: rgba(0, 0, 0, 0.5);
}

/* Gesture Indicators */
.gesture-indicator {
  position: absolute;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  background: rgba(0, 0, 0, 0.75);
  border-radius: 12px;
  padding: 16px 12px;
  z-index: 20;
  pointer-events: none;
}

.volume-indicator {
  color: var(--vp-accent);
}

.brightness-indicator {
  color: #fbbf24;
}

.gi-icon {
  flex-shrink: 0;
}

.gi-bar {
  width: 4px;
  height: 60px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  overflow: hidden;
  display: flex;
  flex-direction: column-reverse;
}

.gi-bar-fill {
  width: 100%;
  background: currentColor;
  border-radius: 2px;
  transition: height 0.1s;
}

.gi-value {
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  color: var(--vp-text);
  font-variant-numeric: tabular-nums;
}

.gesture-fade-enter-active,
.gesture-fade-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}

.gesture-fade-enter-from,
.gesture-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.8);
}

/* Seek Indicator */
.seek-indicator {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--vp-text);
  pointer-events: none;
  z-index: 20;
}

.seek-indicator span {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  background: rgba(0, 0, 0, 0.6);
  padding: 4px 12px;
  border-radius: var(--radius-md);
}

.seek-indicator-fade-enter-active,
.seek-indicator-fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}

.seek-indicator-fade-enter-from,
.seek-indicator-fade-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.7);
}

/* Top Overlay */
.video-top-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  padding: var(--space-3) var(--space-4);
  background: linear-gradient(to bottom, rgba(0, 0, 0, 0.7), transparent);
  pointer-events: none;
}

.video-title-overlay {
  font-size: var(--text-base);
  font-weight: var(--weight-medium);
}

/* Learning Objectives Overlay */
.learning-objectives-overlay {
  position: absolute;
  top: var(--space-4);
  left: var(--space-4);
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  background: rgba(0, 0, 0, 0.75);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-md);
  padding: var(--space-3) var(--space-4);
  max-width: 280px;
  pointer-events: none;
  z-index: 10;
}

.obj-icon {
  color: var(--vp-accent);
  flex-shrink: 0;
  margin-top: 2px;
}

.obj-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.obj-label {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--vp-accent);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.obj-text {
  font-size: var(--text-sm);
  color: var(--vp-text);
  line-height: 1.4;
}

.obj-fade-enter-active,
.obj-fade-leave-active {
  transition: opacity 0.5s ease, transform 0.5s ease;
}

.obj-fade-enter-from,
.obj-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* Custom Controls */
.video-controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: var(--space-3) var(--space-4);
  background: linear-gradient(to top, rgba(0, 0, 0, 0.85), rgba(0, 0, 0, 0.4) 60%, transparent);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  opacity: 0;
  transition: opacity 0.3s;
}

.video-controls.visible {
  opacity: 1;
}

/* Progress Track */
.progress-track {
  position: relative;
  height: 6px;
  background: var(--vp-progress-bg);
  border-radius: var(--radius-sm);
  cursor: pointer;
  margin-bottom: var(--space-3);
  transition: height 0.2s ease;
}

.progress-track:hover {
  height: 8px;
}

.progress-track:hover .progress-thumb {
  opacity: 1;
  transform: translateY(-50%) scale(1);
}

.progress-buffer {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: var(--vp-progress-buffered);
  border-radius: var(--radius-sm);
}

.progress-played {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: var(--vp-accent);
  border-radius: var(--radius-sm);
}

.progress-thumb {
  position: absolute;
  right: -6px;
  top: 50%;
  width: 12px;
  height: 12px;
  background: var(--vp-accent);
  border-radius: var(--radius-circle);
  transform: translateY(-50%) scale(0);
  opacity: 0;
  transition: all 0.2s;
}

/* Controls Row */
.controls-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.controls-left,
.controls-right {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.ctrl-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: transparent;
  border: none;
  color: var(--vp-text);
  cursor: pointer;
  border-radius: var(--radius-sm);
  transition: background 0.2s, transform 0.2s;
}

.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.1);
  transform: scale(1.1);
}

.ctrl-btn.active {
  color: var(--vp-accent);
}

.speed-ctrl-btn {
  width: auto;
  padding: 0 var(--space-3);
  font-size: var(--text-sm);
}

/* Volume */
.volume-control {
  display: flex;
  align-items: center;
  gap: var(--space-1);
}

.volume-slider {
  width: 80px;
  --el-slider-main-bg-color: var(--vp-accent);
  --el-slider-runway-bg-color: var(--vp-progress-bg);
}

.volume-slider :deep(.el-slider__bar) {
  background: var(--vp-accent);
}

/* Time Display */
.time-display {
  font-size: var(--text-sm);
  color: var(--vp-text);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* Subtitles */
.video-subtitles {
  position: absolute;
  bottom: 80px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.75);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-sm);
  font-size: var(--text-base);
  max-width: 80%;
  text-align: center;
}

/* Chapter Chips */
.chapter-chips {
  display: flex;
  gap: var(--space-2);
  flex-wrap: wrap;
  max-width: 1280px;
  width: 100%;
}

.chapter-chip {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  background: var(--vp-surface);
  border: 1px solid var(--vp-border);
  border-radius: var(--radius-xl);
  cursor: pointer;
  font-size: var(--text-sm);
  transition: all 0.2s;
}

.chapter-chip:hover {
  border-color: var(--vp-accent);
}

.chapter-chip.is-active {
  background: var(--vp-accent);
  border-color: var(--vp-accent);
  color: var(--el-color-white);
}

.chapter-chip.is-completed {
  color: var(--vp-text-secondary);
}

.chip-index {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.1);
  border-radius: var(--radius-circle);
  font-size: var(--text-xs);
}

.chip-title {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-check {
  color: var(--el-color-success);
}

/* P1-13: Watermark Overlay — 半透明前端提示性水印 */
.video-watermark-overlay {
  position: absolute;
  bottom: 50px;
  right: 12px;
  z-index: 5;
  pointer-events: none;
  opacity: 0.45;
}

.watermark-text {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.5);
}

/* Video Info Card */
.video-info-card {
  max-width: 1280px;
  width: 100%;
  background: var(--vp-surface);
  border: 1px solid var(--vp-border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}

.info-row {
  display: flex;
  gap: var(--space-2);
  font-size: var(--text-base);
  padding: var(--space-1) 0;
}

.info-label {
  color: var(--vp-text-secondary);
}

.info-value {
  color: var(--vp-text);
}

/* Sidebar */
.player-sidebar {
  width: 360px;
  border-left: 1px solid var(--vp-border);
  background: var(--vp-surface);
  display: flex;
  flex-direction: column;
}

.pc-sidebar {
  display: flex;
}

.sidebar-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.sidebar-tabs :deep(.el-tabs__header) {
  margin: 0;
  background: var(--vp-bg);
  border-bottom: 1px solid var(--vp-border);
}

.sidebar-tabs :deep(.el-tabs__nav-wrap) {
  background: var(--vp-bg);
}

.sidebar-tabs :deep(.el-tabs__item) {
  color: var(--vp-text-secondary);
  font-size: var(--text-base);
  height: 48px;
  line-height: 48px;
  padding: 0 var(--space-4);
}

.sidebar-tabs :deep(.el-tabs__item.is-active) {
  color: var(--vp-accent);
}

.sidebar-tabs :deep(.el-tabs__active-bar) {
  background: var(--vp-accent);
}

.sidebar-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.sidebar-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.tab-content {
  padding: var(--space-3);
  overflow-y: auto;
}

.tab-empty {
  text-align: center;
  color: var(--vp-text-secondary);
  padding: var(--space-7) var(--space-4);
  font-size: var(--text-base);
}

/* Chapter Item */
.chapter-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background 0.2s;
}

.chapter-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.chapter-item.is-active {
  background: rgba(99, 102, 241, 0.15);
  border-left: 3px solid var(--vp-accent);
}

.chapter-item.is-completed .chapter-check {
  color: var(--el-color-success);
}

.chapter-index {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--vp-border);
  border-radius: var(--radius-circle);
  font-size: var(--text-xs);
  flex-shrink: 0;
}

.chapter-item.is-active .chapter-index {
  background: var(--vp-accent);
}

.chapter-info {
  flex: 1;
  min-width: 0;
}

.chapter-title {
  font-size: var(--text-base);
  margin-bottom: var(--space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chapter-duration {
  font-size: var(--text-xs);
  color: var(--vp-text-secondary);
}

.chapter-check {
  color: var(--vp-text-secondary);
  flex-shrink: 0;
}

/* Notes */
.note-input-row {
  display: flex;
  gap: var(--space-2);
  align-items: center;
  margin-bottom: var(--space-3);
}

.note-time-btn {
  font-size: var(--text-xs);
  color: var(--vp-accent);
  cursor: pointer;
  padding: var(--space-1) var(--space-2);
  background: rgba(99, 102, 241, 0.1);
  border-radius: var(--radius-sm);
  white-space: nowrap;
}

.note-input {
  flex: 1;
}

.note-input :deep(.el-input__wrapper) {
  background: var(--vp-bg);
  border: 1px solid var(--vp-border);
  box-shadow: none;
}

.note-input :deep(.el-input__inner) {
  color: var(--vp-text);
}

.note-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-3);
  border-radius: var(--radius-sm);
  transition: background 0.2s;
}

.note-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.note-time {
  font-size: var(--text-xs);
  color: var(--vp-accent);
  cursor: pointer;
  flex-shrink: 0;
  padding: 2px 6px;
  background: rgba(99, 102, 241, 0.1);
  border-radius: var(--radius-sm);
}

.note-content {
  flex: 1;
  font-size: var(--text-base);
  line-height: var(--leading-normal);
}

/* Discussions */
.discussion-item {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  margin-bottom: var(--space-2);
  background: rgba(255, 255, 255, 0.03);
  cursor: pointer;
  transition: background 0.2s;
}

.discussion-item:hover {
  background: rgba(255, 255, 255, 0.08);
}

.discussion-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--space-2);
}

.discussion-author {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
}

.discussion-time {
  font-size: var(--text-xs);
  color: var(--vp-text-secondary);
}

.discussion-title {
  font-size: var(--text-base);
  margin-bottom: var(--space-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.discussion-preview {
  font-size: var(--text-xs);
  color: var(--vp-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* H5 Bottom Tabs */
.h5-bottom-tabs {
  display: none;
}

/* Speed Toast */
.speed-toast {
  position: fixed;
  top: 60px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.9);
  color: var(--el-color-white);
  padding: 8px 20px;
  border-radius: var(--radius-2xl);
  font-size: var(--text-md);
  font-weight: var(--weight-medium);
  z-index: 9999;
  pointer-events: none;
}

.toast-fade-enter-active,
.toast-fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}

.toast-fade-enter-from,
.toast-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-10px);
}

/* ============================================================
   Responsive: H5 (<= 768px)
   ============================================================ */
@media (max-width: 768px) {
  .player-body {
    flex-direction: column;
  }

  .pc-header {
    display: none;
  }

  .h5-header {
    display: flex;
  }

  .player-main {
    padding: 0;
    max-width: 100%;
  }

  .video-container {
    max-width: 100%;
    border-radius: 0;
  }

  .chapter-chips {
    display: none;
  }

  .video-info-card {
    display: none;
  }

  .player-sidebar {
    display: none;
  }

  .h5-bottom-tabs {
    display: block;
    background: var(--vp-surface);
    border-top: 1px solid var(--vp-border);
  }

  .h5-tabs {
    --el-tabs-header-height: 48px;
  }

  .h5-tabs :deep(.el-tabs__header) {
    margin: 0;
  }

  .h5-tabs :deep(.el-tabs__nav-wrap) {
    background: var(--vp-bg);
  }

  .h5-tabs :deep(.el-tabs__item) {
    color: var(--vp-text-secondary);
    font-size: var(--text-base);
    height: 48px;
    line-height: 48px;
  }

  .h5-tabs :deep(.el-tabs__item.is-active) {
    color: var(--vp-accent);
  }

  .h5-tabs :deep(.el-tabs__active-bar) {
    background: var(--vp-accent);
  }

  .h5-tabs :deep(.el-tabs__content) {
    padding: var(--space-3);
    max-height: 300px;
    overflow-y: auto;
  }

  .h5-chapters,
  .h5-notes,
  .h5-discussions {
    padding-bottom: 24px;
  }

  .h5-chapter-item,
  .h5-note-item,
  .h5-discussion-item {
    margin-bottom: var(--space-2);
  }

  .volume-slider {
    width: 60px;
  }

  .time-display {
    font-size: var(--text-xs);
  }

  .ctrl-btn {
    width: 32px;
    height: 32px;
  }

  .ctrl-btn svg {
    width: 16px;
    height: 16px;
  }

  .video-title-overlay {
    font-size: var(--text-xs);
  }
}

/* ============================================================
   P1-6: Portrait Orientation — Enlarged controls for portrait mode
   ============================================================ */
@media (orientation: portrait) {
  .video-controls {
    padding: var(--space-5) var(--space-5) calc(var(--space-5) + env(safe-area-inset-bottom, 0px));
  }

  .progress-track {
    height: 10px;
    margin-bottom: var(--space-5);
  }

  .controls-row {
    gap: var(--space-3);
  }

  .controls-left,
  .controls-right {
    gap: var(--space-3);
  }

  .ctrl-btn {
    width: 44px;
    height: 44px;
  }

  .ctrl-btn svg {
    width: 22px;
    height: 22px;
  }

  .time-display {
    font-size: var(--text-base);
  }

  .volume-slider {
    width: 100px;
  }

  .speed-ctrl-btn {
    font-size: var(--text-base);
    padding: 0 var(--space-4);
  }
}

/* ============================================================
   Dropdown overrides for dark theme
   ============================================================ */
:deep(.el-dropdown-menu) {
  background: var(--vp-surface);
  border: 1px solid var(--vp-border);
}

:deep(.el-dropdown-menu__item) {
  color: var(--vp-text);
  font-size: var(--text-sm);
}

:deep(.el-dropdown-menu__item:hover) {
  background: rgba(255, 255, 255, 0.05);
  color: var(--vp-text);
}

:deep(.el-dropdown-menu__item.active) {
  color: var(--vp-accent);
  background: rgba(99, 102, 241, 0.15);
  font-weight: var(--weight-semibold);
}

/* Element Plus overrides */
:deep(.el-button--primary) {
  --el-button-bg-color: var(--vp-accent);
  --el-button-border-color: var(--vp-accent);
  --el-button-hover-bg-color: var(--vp-accent-light);
  --el-button-hover-border-color: var(--vp-accent-light);
}

:deep(.el-input__wrapper) {
  background: var(--vp-bg);
  box-shadow: none;
  border: 1px solid var(--vp-border);
}

:deep(.el-input__inner) {
  color: var(--vp-text);
}

:deep(.el-input__wrapper:hover) {
  box-shadow: none;
  border-color: var(--vp-accent);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--vp-accent);
  border-color: var(--vp-accent);
}

:deep(.el-slider__runway) {
  background: var(--vp-progress-bg);
}

:deep(.el-slider__bar) {
  background: var(--vp-accent);
}

:deep(.el-slider__button) {
  border-color: var(--vp-accent);
}
</style>
