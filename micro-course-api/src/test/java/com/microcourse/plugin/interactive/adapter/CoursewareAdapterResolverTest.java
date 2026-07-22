package com.microcourse.plugin.interactive.adapter;

import com.microcourse.plugin.interactive.service.CoursewareQueryService;
import com.microcourse.plugin.interactive.service.HtmlCoursewareService;
import com.microcourse.plugin.interactive.service.PptCoursewareService;
import com.microcourse.plugin.interactive.service.SlideService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoursewareAdapterResolverTest {

    private PptCoursewareService pptService;
    private HtmlCoursewareService htmlService;
    private SlideService slideService;
    private CoursewareQueryService queryService;
    private CoursewareAdapterResolver resolver;

    @BeforeEach
    void setup() {
        pptService = mock(PptCoursewareService.class);
        htmlService = mock(HtmlCoursewareService.class);
        slideService = mock(SlideService.class);
        queryService = mock(CoursewareQueryService.class);

        PptCoursewareAdapter pptAdapter = new PptCoursewareAdapter(pptService, queryService);
        HtmlCoursewareAdapter htmlAdapter = new HtmlCoursewareAdapter(htmlService, queryService);
        LegacyCoursewareAdapter legacyAdapter = new LegacyCoursewareAdapter(slideService,
            mock(com.microcourse.plugin.interactive.mapper.SlidePageMapper.class),
            queryService);
        resolver = new CoursewareAdapterResolver(pptAdapter, htmlAdapter, legacyAdapter, queryService);
    }

    @Test
    void resolveByType_returnsCorrectAdapter() {
        assertEquals("PPT", resolver.resolveByType("PPT").type());
        assertEquals("PPT", resolver.resolveByType("ppt").type());  // case insensitive
        assertEquals("HTML", resolver.resolveByType("HTML").type());
        assertEquals("LEGACY", resolver.resolveByType("LEGACY").type());
        assertEquals("LEGACY", resolver.resolveByType(null).type());
        assertEquals("LEGACY", resolver.resolveByType("UNKNOWN").type());
    }

    @Test
    void all_returnsThreeAdapters() {
        assertEquals(3, resolver.list().size());
        assertTrue(resolver.all().containsKey("PPT"));
        assertTrue(resolver.all().containsKey("HTML"));
        assertTrue(resolver.all().containsKey("LEGACY"));
    }
}
