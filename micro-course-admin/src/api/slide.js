/**
 * slide API 转发文件
 * 
 * 项目规范要求所有 API 封装位于 src/api/ 目录下。
 * 此文件从 @/plugins/interactive/api/slide 重新导出所有函数，
 * 确保 src/api/ 路径可用，同时保持向后兼容。
 */
export {
  uploadSlide,
  uploadHtml,
  getSlides,
  getSlidePages,
  getSlidePage,
  generateNarration,
  updateNarration,
  generateAllNarrations,
  generateAudio,
  generateAllAudio,
  updateSlidePage,
  getNarrationSettings,
  updateNarrationSettings
} from '@/plugins/interactive/api/slide'
