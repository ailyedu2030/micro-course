package com.microcourse.dto.invite;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class AcceptWithChaptersRequest {
    @NotEmpty(message = "章节决策不能为空")
    @Valid
    private List<ChapterDecisionItem> chapterDecisions;

    public List<ChapterDecisionItem> getChapterDecisions() { return chapterDecisions; }
    public void setChapterDecisions(List<ChapterDecisionItem> chapterDecisions) { this.chapterDecisions = chapterDecisions; }

    public static class ChapterDecisionItem {
        private Long chapterId;
        private String source;   // "existing" or "new"
        private Long sourceChapterId; // 选existing时

        public Long getChapterId() { return chapterId; }
        public void setChapterId(Long chapterId) { this.chapterId = chapterId; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public Long getSourceChapterId() { return sourceChapterId; }
        public void setSourceChapterId(Long sourceChapterId) { this.sourceChapterId = sourceChapterId; }
    }
}
