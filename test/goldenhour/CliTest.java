package goldenhour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * CLI end-to-end on the saved snapshots (offline mode): real commands,
 * exact outputs. The snapshot covers Mumbai 11–17 Nov 2026.
 */
class CliTest {

    private record Run(int code, String out, String err) {
    }

    private Run run(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int code = Main.run(args,
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
        return new Run(code,
                outBytes.toString(StandardCharsets.UTF_8),
                errBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void helpAndVersion() {
        assertEquals(0, run("--help").code());
        assertTrue(run("--help").out().contains("GoldenHour"));
        Run v = run("--version");
        assertEquals(0, v.code());
        assertEquals("goldenhour 1.0.0", v.out().trim());
    }

    @Test
    void todayFromSnapshot() {
        Run r = run("--from-file", "examples", "today", "--date", "2026-11-11");
        assertEquals(0, r.code(), r.err());
        // Mumbai 11 Nov 2026, IST.
        assertTrue(r.out().contains("Mumbai — Wed 11 Nov 2026"), r.out());
        assertTrue(r.out().contains("Sunrise 06:43   Sunset 18:01   Solar noon 12:22   Day length 11h 17m"), r.out());
        assertTrue(r.out().contains("Best light: evening golden hour 17:01–18:01, quality 100/100 (Superb)"), r.out());
        assertTrue(r.out().contains("Morning blue hour"), r.out());
        assertTrue(r.out().contains("06:20–06:43"), r.out());
        assertTrue(r.out().contains("Morning golden hour"), r.out());
        assertTrue(r.out().contains("06:43–07:43"), r.out());
        assertTrue(r.out().contains("Evening golden hour"), r.out());
        assertTrue(r.out().contains("17:01–18:01"), r.out());
        assertTrue(r.out().contains("Evening blue hour"), r.out());
        assertTrue(r.out().contains("18:01–18:24"), r.out());
        assertTrue(r.out().contains("cloud cover only"), r.out());
    }

    @Test
    void todayJsonFromSnapshot() {
        Run r = run("--from-file", "examples", "--json", "today", "--date", "2026-11-11");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("\"place\": \"Mumbai\""), r.out());
        assertTrue(r.out().contains("\"sunrise\": \"06:43\""), r.out());
        assertTrue(r.out().contains("\"sunset\": \"18:01\""), r.out());
        assertTrue(r.out().contains("\"solar_noon\": \"12:22\""), r.out());
        assertTrue(r.out().contains("\"day_length_minutes\": 677"), r.out());
        assertTrue(r.out().contains("\"kind\": \"evening-golden\""), r.out());
        assertTrue(r.out().contains("\"quality\": 100"), r.out());
        assertTrue(r.out().contains("\"verdict\": \"Superb\""), r.out());
        // The JSON output itself must parse.
        Json.obj(Json.parse(r.out()));
    }

    @Test
    void todayCloudyDay() {
        Run r = run("--from-file", "examples", "today", "--date", "2026-11-15");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Best light: morning blue hour 06:22–06:45, quality 93/100 (Superb)"), r.out());
        assertTrue(r.out().contains("Morning golden hour"), r.out());
        assertTrue(r.out().contains("06:45–07:45"), r.out());
        assertTrue(r.out().contains("89/100 Great"), r.out());
        assertTrue(r.out().contains("Evening golden hour"), r.out());
        assertTrue(r.out().contains("0/100 Skip"), r.out());
    }

    @Test
    void weekFromSnapshot() {
        Run r = run("--from-file", "examples", "week", "--date", "2026-11-11");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Mumbai — next 7 days"), r.out());
        assertTrue(r.out().contains("Wed 11 Nov"), r.out());
        assertTrue(r.out().contains("Tue 17 Nov"), r.out());
        // 15 Nov evening is fully clouded: its best window is the morning blue hour.
        assertTrue(r.out().contains("Sun 15 Nov"), r.out());
        assertTrue(r.out().contains("Morning blue hour 06:22–06:45 93/100 Superb"), r.out());
        assertTrue(r.out().contains("Best day: Wed 11 Nov 2026"), r.out());
        assertTrue(r.out().contains("evening golden hour 17:01–18:01, quality 100/100 (Superb)"), r.out());
    }

    @Test
    void weekDefaultsToTodayWhenNoDate() {
        // Snapshots start 11 Nov; without --date the CLI uses today (11 Nov in the run).
        Run r = run("--from-file", "examples", "week");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("next 7 days"), r.out());
    }

    @Test
    void citiesListsPresets() {
        Run r = run("cities");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("Mumbai"), r.out());
        assertTrue(r.out().contains("19.0760, 72.8777"), r.out());
        assertTrue(r.out().contains("Leh"), r.out());
        assertTrue(r.out().contains("Hampi"), r.out());
    }

    @Test
    void unknownCityExits2() {
        Run r = run("--from-file", "examples", "today", "--city", "Atlantis");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("unknown place 'Atlantis'"), r.err());
    }

    @Test
    void unknownCommandExits2() {
        Run r = run("frobnicate");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("unknown command"), r.err());
    }

    @Test
    void unknownOptionExits2() {
        Run r = run("--bogus");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("unknown option"), r.err());
    }

    @Test
    void badDateExits2() {
        Run r = run("today", "--date", "11/11/2026");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("--date needs YYYY-MM-DD"), r.err());
    }

    @Test
    void latWithoutLonExits2() {
        Run r = run("today", "--lat", "19.07");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("--lat and --lon go together"), r.err());
    }

    @Test
    void latOutOfRangeExits2() {
        Run r = run("today", "--lat", "91", "--lon", "72");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("--lat must be between -90 and 90"), r.err());
    }

    @Test
    void cityAndLatConflictExits2() {
        Run r = run("today", "--city", "Mumbai", "--lat", "19.07", "--lon", "72.87");
        assertEquals(2, r.code());
        assertTrue(r.err().contains("not both"), r.err());
    }

    @Test
    void dateOutsideSnapshotsExits1() {
        Run r = run("--from-file", "examples", "today", "--date", "2026-12-25");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("No snapshot for 2026-12-25"), r.err());
    }

    @Test
    void missingSnapshotDirExits1() {
        Run r = run("--from-file", "examples/nope", "today", "--date", "2026-11-11");
        assertEquals(1, r.code());
        assertTrue(r.err().contains("Cannot list snapshot directory"), r.err());
    }

    @Test
    void customCoordsFromSnapshot() {
        // Custom coords still replay the snapshot (place label is the coords).
        Run r = run("--from-file", "examples", "today", "--lat", "19.076", "--lon", "72.8777",
                "--date", "2026-11-11");
        assertEquals(0, r.code(), r.err());
        assertTrue(r.out().contains("19.0760, 72.8777"), r.out());
        assertTrue(r.out().contains("Sunrise 06:43"), r.out());
    }
}
