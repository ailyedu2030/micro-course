package com.microcourse.plugin.interactive.dto;

import java.util.List;

/**
 * TTS 音色/模型契约 VO（P0-5 / R-6）。
 * 后端为唯一真相，前端下拉据此渲染；消除 MiniMax-speech-01/male-young 等非法枚举。
 */
public class TtsOptionsVO {

    public static class VoiceOption {
        private String id;
        private String label;
        public VoiceOption() {}
        public VoiceOption(String id, String label) { this.id = id; this.label = label; }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
    }

    private List<String> models;
    private List<VoiceOption> voices;
    private String defaultModel;
    private String defaultVoice;

    public TtsOptionsVO() {}

    public List<String> getModels() { return models; }
    public void setModels(List<String> models) { this.models = models; }
    public List<VoiceOption> getVoices() { return voices; }
    public void setVoices(List<VoiceOption> voices) { this.voices = voices; }
    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public String getDefaultVoice() { return defaultVoice; }
    public void setDefaultVoice(String defaultVoice) { this.defaultVoice = defaultVoice; }
}
