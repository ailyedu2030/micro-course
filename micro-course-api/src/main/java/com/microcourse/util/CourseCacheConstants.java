package com.microcourse.util;

public final class CourseCacheConstants {
    private CourseCacheConstants() {}
    public static final String COURSE_CACHE_PREFIX = "mc:course:detail:";
    public static final long COURSE_CACHE_TTL = 300;
    public static final String COURSE_STATS_CACHE_PREFIX = "mc:course:stats:";
    public static final long COURSE_STATS_CACHE_TTL = 60;
}
