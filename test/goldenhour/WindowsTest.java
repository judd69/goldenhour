package goldenhour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Window maths and quality scores, checked against the real Mumbai snapshot. */
class WindowsTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private SunData.SunDay mumbai11Nov() throws Exception {
        String body = Files.readString(Path.of("examples", "sun-mumbai-2026-11-11.json"));
        return SunData.parseSunDay(body, LocalDate.of(2026, 11, 11));
    }

    private SunData.CloudData cloudWeek() throws Exception {
        return SunData.parseCloud(Files.readString(Path.of("examples", "cloud-mumbai-7d.json")));
    }

    @Test
    void windowsForMumbai11Nov() throws Exception {
        List<Windows.Window> windows = Windows.windowsFor(mumbai11Nov(), cloudWeek(), IST);
        assertEquals(4, windows.size());
        // Morning blue hour: civil twilight begin 06:20 -> sunrise 06:43 IST.
        assertEquals("06:20", windows.get(0).start().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        assertEquals("06:43", windows.get(0).end().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
        // Morning golden hour: sunrise 06:43 -> 07:43.
        assertEquals(Windows.MORNING_GOLDEN, windows.get(1).kind());
        assertEquals(LocalDateTime.of(2026, 11, 11, 6, 43), windows.get(1).start());
        assertEquals(LocalDateTime.of(2026, 11, 11, 7, 43), windows.get(1).end());
        // Evening golden hour: sunset 18:01, so 17:01 -> 18:01.
        assertEquals(Windows.EVENING_GOLDEN, windows.get(2).kind());
        assertEquals(LocalDateTime.of(2026, 11, 11, 17, 1), windows.get(2).start());
        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 1), windows.get(2).end());
        // Evening blue hour: sunset 18:01 -> civil end 18:24.
        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 1), windows.get(3).start());
        assertEquals(LocalDateTime.of(2026, 11, 11, 18, 24), windows.get(3).end());
    }

    @Test
    void clearDayScores100Superb() throws Exception {
        // 11 Nov Mumbai: 0% cloud at every golden-hour hour.
        List<Windows.Window> windows = Windows.windowsFor(mumbai11Nov(), cloudWeek(), IST);
        for (Windows.Window w : windows) {
            assertEquals(Integer.valueOf(100), w.quality(), w.label());
            assertEquals("Superb", w.verdict(), w.label());
        }
    }

    @Test
    void cloudyEveningScoresSkip() throws Exception {
        // 15 Nov: 100% cloud at 17:00, 16% at 07:00.
        List<SunData.SunDay> days = SunData.parseSunWeek(
                Files.readString(Path.of("examples", "sun-mumbai-7d.json")));
        SunData.SunDay nov15 = days.get(4);
        assertEquals(LocalDate.of(2026, 11, 15), nov15.date());
        List<Windows.Window> windows = Windows.windowsFor(nov15, cloudWeek(), IST);
        Windows.Window eveningGolden = windows.get(2);
        assertEquals(Integer.valueOf(0), eveningGolden.quality());
        assertEquals("Skip", eveningGolden.verdict());
        Windows.Window morningGolden = windows.get(1);
        // Morning golden 06:45-07:45 overlaps the 06:00 (7%) and 07:00 (16%) hours:
        // mean 11.5 -> quality 89.
        assertEquals(Integer.valueOf(89), morningGolden.quality());
        assertEquals("Great", morningGolden.verdict());
    }

    @Test
    void verdictBands() {
        assertEquals("Superb", Windows.verdictFor(100));
        assertEquals("Superb", Windows.verdictFor(90));
        assertEquals("Great", Windows.verdictFor(89));
        assertEquals("Great", Windows.verdictFor(75));
        assertEquals("Fair", Windows.verdictFor(74));
        assertEquals("Fair", Windows.verdictFor(55));
        assertEquals("Poor", Windows.verdictFor(54));
        assertEquals("Poor", Windows.verdictFor(35));
        assertEquals("Skip", Windows.verdictFor(34));
        assertEquals("Skip", Windows.verdictFor(0));
    }

    @Test
    void bestOfPrefersEveningGoldenOnTie() throws Exception {
        List<Windows.Window> windows = Windows.windowsFor(mumbai11Nov(), cloudWeek(), IST);
        Windows.Window best = Windows.bestOf(windows);
        assertEquals(Windows.EVENING_GOLDEN, best.kind()); // all 100, evening golden wins the tie
    }

    @Test
    void bestOfPicksHighestQuality() throws Exception {
        List<SunData.SunDay> days = SunData.parseSunWeek(
                Files.readString(Path.of("examples", "sun-mumbai-7d.json")));
        List<Windows.Window> windows = Windows.windowsFor(days.get(4), cloudWeek(), IST);
        Windows.Window best = Windows.bestOf(windows);
        // 15 Nov: the morning blue hour (93) beats the morning golden hour (89)
        // because the 06:00 cloud (7%) is clearer than the 07:00 cloud (16%).
        assertEquals(Windows.MORNING_BLUE, best.kind());
        assertEquals(Integer.valueOf(93), best.quality());
    }

    @Test
    void noCloudDataMeansNoScore() throws Exception {
        List<Windows.Window> windows = Windows.windowsFor(mumbai11Nov(), null, IST);
        for (Windows.Window w : windows) {
            assertNull(w.quality());
            assertEquals("No cloud data", w.verdict());
        }
        assertNull(Windows.bestOf(windows));
    }

    @Test
    void dayLengthFormats() {
        assertEquals("11h 17m", Windows.formatDayLength(java.time.Duration.ofSeconds(40663)));
        assertEquals("10h 05m", Windows.formatDayLength(java.time.Duration.ofMinutes(605)));
    }

    @Test
    void windowsAreChronological() throws Exception {
        List<Windows.Window> windows = Windows.windowsFor(mumbai11Nov(), cloudWeek(), IST);
        for (int i = 1; i < windows.size(); i++) {
            assertTrue(windows.get(i).start().isAfter(windows.get(i - 1).start()));
        }
    }
}
