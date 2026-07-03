package com.microcourse.enums;

public enum AttendanceStatus {
    PRESENT("PRESENT"),
    LATE("LATE"),
    ABSENT("ABSENT"),
    EXCUSED("EXCUSED");

    private final String value;

    AttendanceStatus(String value) { this.value = value; }

    public String getValue() { return value; }

    public static AttendanceStatus fromValue(String value) {
        for (AttendanceStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown attendance status: " + value);
    }
}
