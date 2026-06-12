<!--
  视频播放器 - PC + H5 沉浸式深色主题
  路由路径: /student/video/:id
  role-video 主题: 黑底 + 白字 + 强调色 #6366f1
-->
<template>
  <div class="video-player-root role-video">
    <!-- Loading State -->
    <div v-if="loading" class="player-loading">
      <div class="loading-spinner"></div>
      <span class="loading-text">视频加载中...</span>
    </div>

    <!-- Error State -->
    <div v-else-if="errorMsg" class="player-error">
      <div class="error-icon">
        <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <circle cx="12" cy="12" r="10"/>
          <line x1="12" y1="8" x2="12" y2="12"/>
          <line x1="12" y1="16" x2="12.01" y2="16"/>
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
              <polyline points="15 18 9 12 15 6"/>
            </svg>
          </el-button>
          <span class="header-title">{{ videoData.title || '视频加载中' }}</span>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="changeSpeed">
            <span class="speed-btn">
              {{ playbackRate }}x
              <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polyline points="6 9 12 15 18 9"/>
              </svg>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="0.5" :class="{ active: playbackRate === 0.5 }">0.5x</el-dropdown-item>
                <el-dropdown-item :command="0.75" :class="{ active: playbackRate === 0.75 }">0.75x</el-dropdown-item>
                <el-dropdown-item :command="1" :class="{ active: playbackRate === 1 }">1x</el-dropdown-item>
                <el-dropdown-item :command="1.25" :class="{ active: playbackRate === 1.25 }">1.25x</el-dropdown-item>
                <el-dropdown-item :command="1.5" :class="{ active: playbackRate === 1.5 }">1.5x</el-dropdown-item>
                <el-dropdown-item :command="2" :class="{ active: playbackRate === 2 }">2x</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button link @click="showChapterList = !showChapterList" aria-label="章节列表">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="8" y1="6" x2="21" y2="6"/>
              <line x1="8" y1="12" x2="21" y2="12"/>
              <line x1="8" y1="18" x2="21" y2="18"/>
              <line x1="3" y1="6" x2="3.01" y2="6"/>
              <line x1="3" y1="12" x2="3.01" y2="12"/>
              <line x1="3" y1="18" x2="3.01" y2="18"/>
            </svg>
          </el-button>
          <el-button link @click="toggleSettings" aria-label="设置">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <circle cx="12" cy="12" r="3"/>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/>
            </svg>
          </el-button>
        </div>
      </header>

      <!-- H5 Header (<= 768px) -->
      <header class="player-header h5-header">
        <el-button class="back-btn" link @click="goBack" aria-label="返回">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="15 18 9 12 15 6"/>
          </svg>
        </el-button>
        <span class="header-title">{{ videoData.title || '视频' }}</span>
        <div class="h5-header-right">
          <el-dropdown trigger="click" @command="changeSpeed">
            <span class="speed-btn">{{ playbackRate }}x</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item :command="0.5" :class="{ active: playbackRate === 0.5 }">0.5x</el-dropdown-item>
                <el-dropdown-item :command="0.75" :class="{ active: playbackRate === 0.75 }">0.75x</el-dropdown-item>
                <el-dropdown-item :command="1" :class="{ active: playbackRate === 1 }">1x</el-dropdown-item>
                <el-dropdown-item :command="1.25" :class="{ active: playbackRate === 1.25 }">1.25x</el-dropdown-item>
                <el-dropdown-item :command="1.5" :class="{ active: playbackRate === 1.5 }">1.5x</el-dropdown-item>
                <el-dropdown-item :command="2" :class="{ active: playbackRate === 2 }">2x</el-dropdown-item>
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
              @waiting="isBuffering = true"
              @playing="isBuffering = false"
              @progress="onProgress"
              @dblclick="togglePlay"
            ></video>

            <!-- Buffering Spinner -->
            <div v-if="isBuffering" class="video-buffering">
              <div class="buffering-spinner"></div>
            </div>

            <!-- Center Play Button (when paused) -->
            <div v-if="!isPlaying && !isBuffering && !loading" class="center-play-btn" @click="togglePlay">
              <svg width="64" height="64" viewBox="0 0 24 24" fill="currentColor">
                <polygon points="5 3 19 12 5 21 5 3"/>
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
                    <circle cx="12" cy="12" r="10"/>
                    <line x1="12" y1="8" x2="12" y2="12"/>
                    <line x1="12" y1="16" x2="12.01" y2="16"/>
                  </svg>
                </div>
                <div class="obj-content">
                  <div class="obj-label">本节目标</div>
                  <div class="obj-text">{{ currentChapter?.description || '掌握核心概念' }}</div>
                </div>
              </div>
            </transition>

            <!-- Custom Controls -->
            <div class="video-controls" :class="{ visible: controlsVisible || !isPlaying }">
              <!-- Progress Bar -->
              <div class="progress-track" @click="seekVideo" ref="progressTrack">
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
                      <rect x="6" y="4" width="4" height="16"/>
                      <rect x="14" y="4" width="4" height="16"/>
                    </svg>
                    <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                      <polygon points="5 3 19 12 5 21 5 3"/>
                    </svg>
                  </button>

                  <!-- Skip Backward -->
                  <button class="ctrl-btn" @click="skipBackward" aria-label="快退10秒">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polygon points="11 19 2 12 11 5 11 19"/>
                      <polygon points="22 19 13 12 22 5 22 19"/>
                    </svg>
                  </button>

                  <!-- Skip Forward -->
                  <button class="ctrl-btn" @click="skipForward" aria-label="快进10秒">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polygon points="13 19 22 12 13 5 13 19"/>
                      <polygon points="2 19 11 12 2 5 2 19"/>
                    </svg>
                  </button>

                  <!-- Volume -->
                  <div class="volume-control">
                    <button class="ctrl-btn" @click="toggleMute" :aria-label="isMuted ? '取消静音' : '静音'">
                      <svg v-if="isMuted || volume === 0" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
                        <line x1="23" y1="9" x2="17" y2="15"/>
                        <line x1="17" y1="9" x2="23" y2="15"/>
                      </svg>
                      <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                        <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
                        <path d="M15.54 8.46a5 5 0 0 1 0 7.07"/>
                        <path d="M19.07 4.93a10 10 0 0 1 0 14.14"/>
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
                        <el-dropdown-item :command="0.5" :class="{ active: playbackRate === 0.5 }">0.5x</el-dropdown-item>
                        <el-dropdown-item :command="0.75" :class="{ active: playbackRate === 0.75 }">0.75x</el-dropdown-item>
                        <el-dropdown-item :command="1" :class="{ active: playbackRate === 1 }">1x</el-dropdown-item>
                        <el-dropdown-item :command="1.25" :class="{ active: playbackRate === 1.25 }">1.25x</el-dropdown-item>
                        <el-dropdown-item :command="1.5" :class="{ active: playbackRate === 1.5 }">1.5x</el-dropdown-item>
                        <el-dropdown-item :command="2" :class="{ active: playbackRate === 2 }">2x</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>

                  <!-- Subtitles -->
                  <button v-if="videoData.subtitleUrl" class="ctrl-btn" :class="{ active: subtitlesEnabled }" @click="toggleSubtitles" aria-label="字幕">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <rect x="2" y="6" width="20" height="12" rx="2"/>
                      <line x1="6" y1="12" x2="18" y2="12"/>
                      <line x1="8" y1="16" x2="16" y2="16"/>
                    </svg>
                  </button>

                  <!-- Fullscreen -->
                  <button class="ctrl-btn" @click="toggleFullscreen" :aria-label="isFullscreen ? '退出全屏' : '全屏'">
                    <svg v-if="!isFullscreen" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="15 3 21 3 21 9"/>
                      <polyline points="9 21 3 21 3 15"/>
                      <line x1="21" y1="3" x2="14" y2="10"/>
                      <line x1="3" y1="21" x2="10" y2="14"/>
                    </svg>
                    <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                      <polyline points="4 14 10 14 10 20"/>
                      <polyline points="20 10 14 10 14 4"/>
                      <line x1="14" y1="10" x2="21" y2="3"/>
                      <line x1="3" y1="21" x2="10" y2="14"/>
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
                <polyline points="20 6 9 17 4 12"/>
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
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="笔记" name="notes">
              <div class="tab-content notes-tab">
                <div class="note-input-row">
                  <span class="note-time-btn" @click="insertNoteAtCurrentTime">{{ formatTime(currentTime) }}</span>
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
                  <span class="note-time" @click="seekToTime(note.time)">{{ formatTime(note.time) }}</span>
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
                    <polyline points="20 6 9 17 4 12"/>
                  </svg>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="笔记" name="notes">
              <div class="tab-content notes-tab h5-notes">
                <div class="note-input-row">
                  <span class="note-time-btn" @click="insertNoteAtCurrentTime">{{ formatTime(currentTime) }}</span>
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
                  <span class="note-time" @click="seekToTime(note.time)">{{ formatTime(note.time) }}</span>
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
import { ElMessage } from 'element-plus'
import Hls from 'hls.js'
import { getVideoById } from '@/api/video'
import { getChapters } from '@/api/chapter'
import { getLearningProgress, updateLearningProgress, createLearningProgress } from '@/api/learning-progress'
import { getPosts } from '@/api/discussion'
import request from '@/utils/request'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// DOM refs
const videoRef = ref(null)
const progressTrack = ref(null)

// Route params
const videoId = computed(() => route.params.id)
const courseId = computed(() => route.query.courseId)
const chapterId = computed(() => route.query.chapterId)

// State
const loading = ref(true)
const errorMsg = ref('')
const videoData = ref({})
const chapters = ref([])
const notes = ref([])
const discussions = ref([])
const activeTab = ref('chapters')
const showChapterList = ref(false)
const isMobile = ref(false)

// Playback state
const isPlaying = ref(false)
const isBuffering = ref(false)
const isMuted = ref(false)
const isFullscreen = ref(false)
const subtitlesEnabled = ref(false)
const currentSubtitle = ref('')
const playbackRate = ref(1)
const volumePercent = ref(100)
const currentTime = ref(0)
const duration = ref(0)
const bufferedPercent = ref(0)
const lastPosition = ref(0)
const currentChapterIndex = ref(0)
const currentChapter = computed(() => chapters.value[currentChapterIndex.value])

// Progress reporting
let progressId = ref(null)
let lastReportedProgress = 0
let progressReportTimer = null
let hideControlsTimer = null
const controlsVisible = ref(true)

// HLS
let hlsInstance = ref(null)

// Learning objectives overlay
const showObjectives = ref(false)
let objectivesTimer = null

// Mobile touch gestures
let touchStartX = 0
let touchStartY = 0
let touchStartTime = 0
let touchStartVolume = 100
let touchStartBrightness = 100
const lastTapTimeout = ref(null)
const lastTapTime = ref(0)

// Learning objectives auto-show on mount
const showObjectivesOverlay = () => {
  showObjectives.value = true
  if (objectivesTimer) clearTimeout(objectivesTimer)
  objectivesTimer = setTimeout(() => {
    showObjectives.value = false
  }, 3000)
}

// Local storage key
const STORAGE_KEY = computed(() => `video_progress_${videoId.value}`)

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

// Video load
const loadVideo = async () => {
  try {
    loading.value = true
    errorMsg.value = ''
    const res = await getVideoById(videoId.value)
    videoData.value = res.data || res

    await nextTick()
    initPlayer()
    await Promise.all([loadChapters(), loadProgress(), loadDiscussions()])
    loadLocalPosition()
    showObjectivesOverlay()
  } catch {
    errorMsg.value = '无法加载视频，请检查网络连接'
  } finally {
    loading.value = false
  }
}

const initPlayer = () => {
  const video = videoRef.value
  const url = videoData.value.hls_url || videoData.value.url

  if (!url) {
    errorMsg.value = '视频地址无效'
    return
  }

  if (isHLS(url)) {
    if (Hls.isSupported()) {
      hlsInstance.value = new Hls()
      hlsInstance.value.loadSource(url)
      hlsInstance.value.attachMedia(video)
      hlsInstance.value.on(Hls.Events.MANIFEST_PARSED, () => {
        video.play().catch(() => {})
      })
      hlsInstance.value.on(Hls.Events.ERROR, (event, data) => {
        if (data.fatal) {
          errorMsg.value = '视频播放出错'
        }
      })
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = url
      video.play().catch(() => {})
    }
  } else {
    video.src = url
    video.load()
  }
}

const isHLS = (url) => {
  return url && (url.endsWith('.m3u8') || url.includes('.m3u8'))
}

const retryLoad = () => {
  loadVideo()
}

// Chapters
const loadChapters = async () => {
  if (!courseId.value) return
  try {
    const res = await getChapters({ courseId: courseId.value })
    const list = res.data?.items || res.data || []
    chapters.value = list.map((c, i) => ({
      ...c,
      isCompleted: false
    }))
    // Mark current chapter
    const idx = chapters.value.findIndex(c => Number(c.id) === Number(chapterId.value))
    if (idx >= 0) currentChapterIndex.value = idx
  } catch {
    chapters.value = []
  }
}

// Progress
const loadProgress = async () => {
  if (!userStore.userInfo?.id || !courseId.value) return
  try {
    const res = await getLearningProgress({
      userId: userStore.userInfo.id,
      courseId: courseId.value
    })
    const list = res.data || []
    const progressData = Array.isArray(list)
      ? list.find(p => Number(p.chapterId) === Number(chapterId.value))
      : {}
    if (progressData?.id) {
      progressId.value = progressData.id
    }
    if (progressData?.videoPosition > 0) {
      lastPosition.value = progressData.videoPosition
    }
  } catch {
    // ignore
  }
}

const ensureProgressRecord = async () => {
  if (progressId.value) return true
  if (!userStore.userInfo?.id || !courseId.value) return false
  try {
    const res = await createLearningProgress({
      userId: userStore.userInfo.id,
      courseId: courseId.value,
      chapterId: chapterId.value,
      videoPosition: 0,
      videoProgress: 0
    })
    progressId.value = (res.data || res).id
    return !!progressId.value
  } catch {
    return false
  }
}

const reportProgress = async () => {
  const video = videoRef.value
  if (!video || !video.duration || video.paused) return
  const current = video.currentTime
  const total = video.duration
  const progressPercentVal = (current / total) * 100
  if (Math.abs(progressPercentVal - lastReportedProgress) < 1) return
  lastReportedProgress = progressPercentVal
  try {
    const hasRecord = await ensureProgressRecord()
    if (!hasRecord) return
    await updateLearningProgress(progressId.value, {
      videoPosition: Math.floor(current),
      videoProgress: Math.round(progressPercentVal)
    })
    saveLocalPosition(current)
  } catch {
    // ignore
  }
}

const startProgressReporting = () => {
  progressReportTimer = setInterval(() => {
    reportProgress()
  }, 30000) // 30 seconds
}

// Local position
const saveLocalPosition = (time) => {
  localStorage.setItem(STORAGE_KEY.value, JSON.stringify({ time, updatedAt: Date.now() }))
}

const loadLocalPosition = () => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY.value)
    if (saved) {
      const { time } = JSON.parse(saved)
      if (time > 0 && time < duration.value - 10) {
        lastPosition.value = time
        const video = videoRef.value
        if (video) {
          video.currentTime = time
        }
      }
    }
  } catch {
    // ignore
  }
}

// Discussions
const loadDiscussions = async () => {
  if (!chapterId.value) return
  try {
    const res = await getPosts({ chapterId: chapterId.value, page: 0, size: 20 })
    discussions.value = res.data?.items || res.data || []
  } catch {
    discussions.value = []
  }
}

// Notes
const noteText = ref('')

const addNote = () => {
  if (!noteText.value.trim()) return
  const note = {
    id: Date.now(),
    time: currentTime.value,
    content: noteText.value.trim(),
    createdAt: new Date().toISOString()
  }
  notes.value.unshift(note)
  noteText.value = ''
  ElMessage.success('笔记已添加')
}

const deleteNote = (id) => {
  notes.value = notes.value.filter(n => n.id !== id)
}

const insertNoteAtCurrentTime = () => {
  noteText.value = ''
}

const highlightTime = (time) => {
  // Could emit event to highlight in video if needed
}

const seekToTime = (time) => {
  const video = videoRef.value
  if (video) {
    video.currentTime = time
  }
}

// Chapter switching
const switchChapter = async (id) => {
  // Save current progress before switching
  await reportProgress()
  router.push({
    query: {
      ...route.query,
      chapterId: id
    }
  })
  // Reload video data for new chapter
  const idx = chapters.value.findIndex(c => Number(c.id) === Number(id))
  if (idx >= 0) {
    currentChapterIndex.value = idx
    chapters.value[idx].isCompleted = false
  }
  await loadVideo()
}

// Playback controls
const togglePlay = () => {
  const video = videoRef.value
  if (!video) return
  if (video.paused) {
    video.play()
    isPlaying.value = true
  } else {
    video.pause()
    isPlaying.value = false
  }
}

const skipBackward = () => {
  const video = videoRef.value
  if (video) {
    video.currentTime = Math.max(video.currentTime - 10, 0)
  }
}

const skipForward = () => {
  const video = videoRef.value
  if (video) {
    video.currentTime = Math.min(video.currentTime + 10, video.duration)
  }
}

const toggleMute = () => {
  const video = videoRef.value
  if (!video) return
  video.muted = !video.muted
  isMuted.value = video.muted
}

const changeVolume = (val) => {
  const video = videoRef.value
  if (video) {
    video.volume = val / 100
    volumePercent.value = val
    isMuted.value = val === 0
  }
}

const changeSpeed = (speed) => {
  playbackRate.value = speed
  const video = videoRef.value
  if (video) {
    video.playbackRate = speed
  }
  speedToastVisible.value = true
  setTimeout(() => {
    speedToastVisible.value = false
  }, 1500)
}

const toggleSubtitles = () => {
  subtitlesEnabled.value = !subtitlesEnabled.value
}

const toggleFullscreen = async () => {
  const video = videoRef.value
  if (!video) return
  try {
    if (!document.fullscreenElement) {
      await video.requestFullscreen?.()
      isFullscreen.value = true
    } else {
      await document.exitFullscreen?.()
      isFullscreen.value = false
    }
  } catch {
    isFullscreen.value = false
  }
}

const seekVideo = (e) => {
  const video = videoRef.value
  const track = progressTrack.value
  if (!video || !track) return
  const rect = track.getBoundingClientRect()
  const percent = (e.clientX - rect.left) / rect.width
  video.currentTime = percent * video.duration
}

// Controls visibility
const showControls = () => {
  controlsVisible.value = true
  if (hideControlsTimer) {
    clearTimeout(hideControlsTimer)
    hideControlsTimer = null
  }
}

const hideControlsDelayed = () => {
  hideControlsTimer = setTimeout(() => {
    if (isPlaying.value) {
      controlsVisible.value = false
    }
  }, 3000)
}

const speedToastVisible = ref(false)

// Event handlers
const onCanPlay = () => {
  const video = videoRef.value
  if (video) {
    duration.value = video.duration
    video.playbackRate = playbackRate.value
    video.volume = volumePercent.value / 100
  }
}

const onTimeUpdate = () => {
  const video = videoRef.value
  if (video) {
    currentTime.value = video.currentTime
  }
}

const onProgress = () => {
  const video = videoRef.value
  if (video && video.buffered.length > 0) {
    bufferedPercent.value = (video.buffered.end(video.buffered.length - 1) / video.duration) * 100
  }
}

const onEnded = async () => {
  isPlaying.value = false
  if (chapters.value[currentChapterIndex.value]) {
    chapters.value[currentChapterIndex.value].isCompleted = true
  }
  await reportProgress()
  ElMessage.success('视频播放完成')
}

const onVideoError = () => {
  errorMsg.value = '视频播放出错，请尝试刷新页面'
}

// Keyboard shortcuts
const handleKeydown = (e) => {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return
  switch (e.code) {
    case 'Space':
      e.preventDefault()
      togglePlay()
      showControls()
      break
    case 'ArrowLeft':
      e.preventDefault()
      skipBackward()
      showControls()
      break
    case 'ArrowRight':
      e.preventDefault()
      skipForward()
      showControls()
      break
    case 'KeyF':
      toggleFullscreen()
      break
    case 'KeyM':
      toggleMute()
      break
  }
}

// Fullscreen change handler
const handleFullscreenChange = () => {
  isFullscreen.value = !!document.fullscreenElement
}

// Navigation
const goBack = () => {
  router.back()
}

const toggleSettings = () => {
  // Could show settings panel
}

// Lifecycle
let resizeTimer = null
const handleResize = () => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    isMobile.value = window.innerWidth <= 768
  }, 200)
}

// Mobile touch handlers
const handleTouchStart = (e) => {
  if (!isMobile.value) return
  const touch = e.touches[0]
  touchStartX = touch.clientX
  touchStartY = touch.clientY
  touchStartTime = Date.now()
  const video = videoRef.value
  if (video) {
    touchStartVolume = video.volume * 100
    touchStartBrightness = 100
  }
}

const handleTouchMove = (e) => {
  if (!isMobile.value) return
  const touch = e.touches[0]
  const deltaX = touch.clientX - touchStartX
  const deltaY = touch.clientY - touchStartY
  const video = videoRef.value
  if (!video) return
  const rect = e.target.closest('.video-container')?.getBoundingClientRect()
  if (!rect) return
  const relativeX = touchStartX - rect.left
  const isRightSide = relativeX > rect.width / 2
  if (Math.abs(deltaX) > Math.abs(deltaY)) {
    // Horizontal swipe - could be used for seeking
  } else {
    // Vertical swipe
    const sensitivity = 0.5
    const delta = -deltaY * sensitivity
    if (isRightSide) {
      // Right side: volume
      const newVol = Math.min(100, Math.max(0, touchStartVolume + delta))
      video.volume = newVol / 100
      volumePercent.value = newVol
      isMuted.value = newVol === 0
    } else {
      // Left side: brightness (CSS filter on container)
      const newBri = Math.min(100, Math.max(20, 100 + delta))
      videoRef.value?.parentElement?.style.setProperty('--brightness', newBri / 100)
    }
  }
}

const handleTouchEnd = (e) => {
  if (!isMobile.value) return
  const touch = e.changedTouches[0]
  const elapsed = Date.now() - touchStartTime
  const deltaX = Math.abs(touch.clientX - touchStartX)
  const deltaY = Math.abs(touch.clientY - touchStartY)

  // Double tap detection
  if (elapsed < 300 && deltaX < 30 && deltaY < 30) {
    const rect = e.target.closest('.video-container')?.getBoundingClientRect()
    if (!rect) return
    const tapX = touch.clientX - rect.left
    const isLeftHalf = tapX < rect.width / 2

    if (lastTapTimeout.value && Date.now() - lastTapTime < 300) {
      // Double tap
      if (isLeftHalf) {
        skipBackward()
      } else {
        skipForward()
      }
      lastTapTimeout.value = null
    } else {
      lastTapTime.value = Date.now()
      lastTapTimeout.value = setTimeout(() => {
        lastTapTimeout.value = null
      }, 300)
    }
  }
}

onMounted(async () => {
  isMobile.value = window.innerWidth <= 768
  await nextTick()
  loadVideo()
  startProgressReporting()
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  // Save final position
  const video = videoRef.value
  if (video) {
    saveLocalPosition(video.currentTime)
    reportProgress()
  }
  if (hlsInstance.value) {
    hlsInstance.value.destroy()
    hlsInstance.value = null
  }
  if (progressReportTimer) {
    clearInterval(progressReportTimer)
    progressReportTimer = null
  }
  if (hideControlsTimer) {
    clearTimeout(hideControlsTimer)
    hideControlsTimer = null
  }
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  window.removeEventListener('resize', handleResize)
  if (resizeTimer) clearTimeout(resizeTimer)
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
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

/* Loading */
.player-loading {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
}

.loading-spinner {
  width: 48px;
  height: 48px;
  border: 3px solid var(--vp-border);
  border-top-color: var(--vp-accent);
  border-radius: var(--radius-circle);
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.loading-text {
  color: var(--vp-text-secondary);
  font-size: var(--text-base);
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
  background: linear-gradient(to top, rgba(0, 0, 0, 0.9), transparent);
  opacity: 0;
  transition: opacity 0.3s;
}

.video-controls.visible {
  opacity: 1;
}

/* Progress Track */
.progress-track {
  position: relative;
  height: 4px;
  background: var(--vp-progress-bg);
  border-radius: var(--radius-sm);
  cursor: pointer;
  margin-bottom: var(--space-3);
}

.progress-track:hover {
  height: 6px;
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
  transition: background 0.2s;
}

.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.1);
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