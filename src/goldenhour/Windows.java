package goldenhour;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Golden-hour and blue-hour windows, and the shoot-quality score.
 *
 * <p>Definitions (the standard ones photographers use):
 * <ul>
 *   <li>Morning golden hour: sunrise → sunrise + 60 minutes.
 *   <li>Evening golden hour: sunset − 60 minutes → sunset.
 *   <li>Morning blue hour: civil twilight begin → sunrise.
 *   <li>Evening blue hour: sunset → civil twilight end.
 * </ul>
 *
 * <p>The quality score is an ESTIMATE from cloud cover only: the mean
 * Open-Meteo hourly cloud cover over the window, quality = 100 − mean%.
 * It says nothing about haze, dust, or pollution — the README and the
 * dashboard say so in plain words. A window with no cloud data gets no
 * score at all, never a guessed one.
 */
public final class Windows {

    private Windows() {
    }

    /** One light window: kind, local start/end, quality 0–100 (null = no cloud data). */
    public record Window(String kind, String label, LocalDateTime start, LocalDateTime end,
                         Integer quality, String verdict) {
    }

    public static final String MORNING_GOLDEN = "morning-golden";
    public static final String EVENING_GOLDEN = "evening-golden";
    public static final String MORNING_BLUE = "morning-blue";
    public static final String EVENING_BLUE = "evening-blue";

    /** Verdict for a quality score: 90+ Superb, 75+ Great, 55+ Fair, 35+ Poor, else Skip. */
    public static String verdictFor(int quality) {
        if (quality >= 90) {
            return "Superb";
        }
        if (quality >= 75) {
            return "Great";
        }
        if (quality >= 55) {
            return "Fair";
        }
        if (quality >= 35) {
            return "Poor";
        }
        return "Skip";
    }

    /**
     * The four windows for one day, in chronological order. {@code cloud}
     * may be null (offline sun-only mode) — then every quality is null.
     * Window boundaries are truncated to the minute: photographers plan
     * in minutes, and the APIs' second precision is false precision here.
     */
    public static List<Window> windowsFor(SunData.SunDay day, SunData.CloudData cloud, ZoneId zone) {
        LocalDateTime sunrise = LocalDateTime.ofInstant(day.sunrise(), zone)
                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        LocalDateTime sunset = LocalDateTime.ofInstant(day.sunset(), zone)
                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        LocalDateTime civilBegin = LocalDateTime.ofInstant(day.civilBegin(), zone)
                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        LocalDateTime civilEnd = LocalDateTime.ofInstant(day.civilEnd(), zone)
                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        List<Window> out = new ArrayList<>();
        out.add(window(MORNING_BLUE, "Morning blue hour", civilBegin, sunrise, cloud));
        out.add(window(MORNING_GOLDEN, "Morning golden hour", sunrise, sunrise.plusMinutes(60), cloud));
        out.add(window(EVENING_GOLDEN, "Evening golden hour", sunset.minusMinutes(60), sunset, cloud));
        out.add(window(EVENING_BLUE, "Evening blue hour", sunset, civilEnd, cloud));
        return out;
    }

    private static Window window(String kind, String label,
                                 LocalDateTime start, LocalDateTime end,
                                 SunData.CloudData cloud) {
        Integer quality = null;
        String verdict = "No cloud data";
        if (cloud != null) {
            Double mean = cloud.meanCloudOver(start, end);
            if (mean != null) {
                quality = (int) Math.round(100 - mean);
                verdict = verdictFor(quality);
            }
        }
        return new Window(kind, label, start, end, quality, verdict);
    }

    /**
     * The best window of the day: highest quality; ties break towards the
     * evening golden hour (the classic shot), then morning golden, then
     * evening blue, then morning blue. Returns null when no window scored.
     */
    public static Window bestOf(List<Window> windows) {
        Window best = null;
        for (Window w : windows) {
            if (w.quality() == null) {
                continue;
            }
            if (best == null
                    || w.quality() > best.quality()
                    || (w.quality().equals(best.quality())
                            && rank(w.kind()) < rank(best.kind()))) {
                best = w;
            }
        }
        return best;
    }

    private static int rank(String kind) {
        return switch (kind) {
            case EVENING_GOLDEN -> 0;
            case MORNING_GOLDEN -> 1;
            case EVENING_BLUE -> 2;
            default -> 3; // morning blue
        };
    }

    /** Formats a duration like 40663s as "11h 17m". */
    public static String formatDayLength(java.time.Duration d) {
        long totalMinutes = d.toMinutes();
        return (totalMinutes / 60) + "h " + String.format("%02d", totalMinutes % 60) + "m";
    }
}
