package goldenhour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parsing of the real saved snapshots (verbatim API responses). */
class SunDataTest {

    private static final String SUN_SINGLE = """
            {"results":{"sunrise":"2026-11-11T01:13:37+00:00","sunset":"2026-11-11T12:31:20+00:00",
            "solar_noon":"2026-11-11T06:52:29+00:00","day_length":40663,
            "civil_twilight_begin":"2026-11-11T00:50:48+00:00","civil_twilight_end":"2026-11-11T12:54:09+00:00",
            "nautical_twilight_begin":"2026-11-11T00:24:27+00:00","nautical_twilight_end":"2026-11-11T13:20:30+00:00",
            "astronomical_twilight_begin":"2026-11-10T23:58:20+00:00","astronomical_twilight_end":"2026-11-11T13:46:38+00:00"},
            "status":"OK","tzid":"UTC"}""";

    private static final String CLOUD_MINI = """
            {"latitude":19.086115,"longitude":72.85291,"timezone":"Asia/Kolkata",
            "hourly":{"time":["2026-11-11T06:00","2026-11-11T07:00","2026-11-11T08:00"],
            "cloud_cover":[0,16,100]}}""";

    @Test
    void parsesSingleDaySunResponse() {
        SunData.SunDay day = SunData.parseSunDay(SUN_SINGLE, LocalDate.of(2026, 11, 11));
        assertEquals(LocalDate.of(2026, 11, 11), day.date());
        assertEquals("2026-11-11T01:13:37Z", day.sunrise().toString());
        assertEquals("2026-11-11T12:31:20Z", day.sunset().toString());
        assertEquals("2026-11-11T06:52:29Z", day.solarNoon().toString());
        assertEquals(40663, day.dayLength().toSeconds());
        assertEquals("2026-11-11T00:50:48Z", day.civilBegin().toString());
        assertEquals("2026-11-11T12:54:09Z", day.civilEnd().toString());
    }

    @Test
    void rejectsNonOkStatus() {
        String bad = SUN_SINGLE.replace("\"status\":\"OK\"", "\"status\":\"INVALID_REQUEST\"");
        assertThrows(IllegalArgumentException.class,
                () -> SunData.parseSunDay(bad, LocalDate.of(2026, 11, 11)));
    }

    @Test
    void parsesWeekSnapshot() throws Exception {
        String body = java.nio.file.Files.readString(
                java.nio.file.Path.of("examples", "sun-mumbai-7d.json"));
        List<SunData.SunDay> days = SunData.parseSunWeek(body);
        assertEquals(7, days.size());
        assertEquals(LocalDate.of(2026, 11, 11), days.get(0).date());
        assertEquals(LocalDate.of(2026, 11, 17), days.get(6).date());
        // Sunrise drifts later across the week: 01:13:37 -> 01:16:55 UTC (time-of-day).
        assertTrue(days.get(6).sunrise().atOffset(java.time.ZoneOffset.UTC).toLocalTime()
                .isAfter(days.get(0).sunrise().atOffset(java.time.ZoneOffset.UTC).toLocalTime()));
        // Sunset drifts earlier: 12:31:20 -> 12:29:53 UTC (time-of-day).
        assertTrue(days.get(6).sunset().atOffset(java.time.ZoneOffset.UTC).toLocalTime()
                .isBefore(days.get(0).sunset().atOffset(java.time.ZoneOffset.UTC).toLocalTime()));
    }

    @Test
    void parsesSavedSingleDaySnapshot() throws Exception {
        String body = java.nio.file.Files.readString(
                java.nio.file.Path.of("examples", "sun-mumbai-2026-11-11.json"));
        SunData.SunDay day = SunData.parseSunDay(body, LocalDate.of(2026, 11, 11));
        assertEquals("2026-11-11T01:13:37Z", day.sunrise().toString());
    }

    @Test
    void parsesCloudResponse() {
        SunData.CloudData cloud = SunData.parseCloud(CLOUD_MINI);
        assertEquals(ZoneId.of("Asia/Kolkata"), cloud.zone());
        assertEquals(3, cloud.times().size());
        assertEquals(Integer.valueOf(16),
                cloud.cloudAt(java.time.LocalDateTime.of(2026, 11, 11, 7, 0)));
        assertNull(cloud.cloudAt(java.time.LocalDateTime.of(2026, 11, 11, 9, 0)));
    }

    @Test
    void meanCloudOverUsesHoursOverlappingWindow() {
        SunData.CloudData cloud = SunData.parseCloud(CLOUD_MINI);
        // Window 07:15-07:45 overlaps only the 07:00 hour -> 16%.
        Double mean = cloud.meanCloudOver(
                java.time.LocalDateTime.of(2026, 11, 11, 7, 15),
                java.time.LocalDateTime.of(2026, 11, 11, 7, 45));
        assertEquals(16.0, mean);
        // Window 06:30-07:30 overlaps the 06:00 (0) and 07:00 (16) hours -> mean 8.
        assertEquals(8.0, cloud.meanCloudOver(
                java.time.LocalDateTime.of(2026, 11, 11, 6, 30),
                java.time.LocalDateTime.of(2026, 11, 11, 7, 30)));
        // No hour overlaps -> null, never a guessed value.
        assertNull(cloud.meanCloudOver(
                java.time.LocalDateTime.of(2026, 11, 11, 9, 0),
                java.time.LocalDateTime.of(2026, 11, 11, 10, 0)));
    }

    @Test
    void parsesSavedCloudSnapshot() throws Exception {
        String body = java.nio.file.Files.readString(
                java.nio.file.Path.of("examples", "cloud-mumbai-7d.json"));
        SunData.CloudData cloud = SunData.parseCloud(body);
        assertEquals(168, cloud.times().size()); // 7 days x 24 hours
        assertEquals(Integer.valueOf(0),
                cloud.cloudAt(java.time.LocalDateTime.of(2026, 11, 11, 7, 0)));
        assertEquals(Integer.valueOf(100),
                cloud.cloudAt(java.time.LocalDateTime.of(2026, 11, 15, 17, 0)));
        assertEquals(Integer.valueOf(16),
                cloud.cloudAt(java.time.LocalDateTime.of(2026, 11, 15, 7, 0)));
    }

    @Test
    void rejectsMismatchedCloudArrays() {
        String bad = CLOUD_MINI.replace("[0,16,100]", "[0,16]");
        assertThrows(IllegalArgumentException.class, () -> SunData.parseCloud(bad));
    }
}
