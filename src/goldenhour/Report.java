package goldenhour;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders GoldenHour results as human-readable text and as JSON.
 * All times are shown in the place's local timezone, 24-hour clock.
 */
public final class Report {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy");

    private Report() {
    }

    /** One day: sun facts + the four windows with quality scores. */
    public static String todayText(Cities.City place, SunData.SunDay day,
                                   List<Windows.Window> windows, ZoneId zone) {
        LocalDateTime sunrise = LocalDateTime.ofInstant(day.sunrise(), zone);
        LocalDateTime sunset = LocalDateTime.ofInstant(day.sunset(), zone);
        LocalDateTime noon = LocalDateTime.ofInstant(day.solarNoon(), zone);
        StringBuilder out = new StringBuilder();
        out.append(String.format("%s — %s (%.4f, %.4f)%n",
                place.name(), day.date().format(DATE), place.latitude(), place.longitude()));
        out.append(String.format("Sunrise %s   Sunset %s   Solar noon %s   Day length %s%n",
                sunrise.format(TIME), sunset.format(TIME), noon.format(TIME),
                Windows.formatDayLength(day.dayLength())));
        Windows.Window best = Windows.bestOf(windows);
        if (best != null) {
            out.append(String.format("Best light: %s %s–%s, quality %d/100 (%s)%n",
                    best.label().toLowerCase(), best.start().format(TIME), best.end().format(TIME),
                    best.quality(), best.verdict()));
        }
        out.append("\n");
        for (Windows.Window w : windows) {
            out.append(String.format("  %-22s %s–%s  %s%n",
                    w.label(), w.start().format(TIME), w.end().format(TIME), qualityText(w)));
        }
        out.append("\nQuality is an estimate from cloud cover only — it says nothing about haze or pollution.\n");
        return out.toString();
    }

    /** Seven days: one line per day with its best window. */
    public static String weekText(Cities.City place, List<SunData.SunDay> days,
                                  SunData.CloudData cloud, ZoneId zone) {
        StringBuilder out = new StringBuilder();
        out.append(String.format("%s — next 7 days (%.4f, %.4f)%n%n", place.name(),
                place.latitude(), place.longitude()));
        out.append(String.format("  %-16s %-11s %-11s  %-26s %s%n",
                "Date", "Sunrise", "Sunset", "Best window", "Quality"));
        Windows.Window weekBest = null;
        SunData.SunDay weekBestDay = null;
        for (SunData.SunDay day : days) {
            List<Windows.Window> windows = Windows.windowsFor(day, cloud, zone);
            Windows.Window best = Windows.bestOf(windows);
            LocalDateTime sunrise = LocalDateTime.ofInstant(day.sunrise(), zone);
            LocalDateTime sunset = LocalDateTime.ofInstant(day.sunset(), zone);
            out.append(String.format("  %-16s %-11s %-11s  %-26s %s%n",
                    day.date().format(DateTimeFormatter.ofPattern("EEE d MMM")),
                    sunrise.format(TIME), sunset.format(TIME),
                    best == null ? "—" : best.label() + " " + best.start().format(TIME)
                            + "–" + best.end().format(TIME),
                    best == null ? "—" : best.quality() + "/100 " + best.verdict()));
            if (best != null && (weekBest == null || best.quality() > weekBest.quality())) {
                weekBest = best;
                weekBestDay = day;
            }
        }
        if (weekBest != null) {
            out.append(String.format("%nBest day: %s — %s %s–%s, quality %d/100 (%s).%n",
                    weekBestDay.date().format(DATE), weekBest.label().toLowerCase(),
                    weekBest.start().format(TIME), weekBest.end().format(TIME),
                    weekBest.quality(), weekBest.verdict()));
        }
        out.append("\nQuality is an estimate from cloud cover only — it says nothing about haze or pollution.\n");
        return out.toString();
    }

    /** The preset cities, one per line. */
    public static String citiesText() {
        StringBuilder out = new StringBuilder("Preset places (use --city NAME):\n");
        for (Cities.City c : Cities.ALL) {
            out.append(String.format("  %-12s %.4f, %.4f  %s%n",
                    c.name(), c.latitude(), c.longitude(), c.zone()));
        }
        return out.toString();
    }

    /** Machine-readable JSON for one day. */
    public static String todayJson(Cities.City place, SunData.SunDay day,
                                   List<Windows.Window> windows, ZoneId zone) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"place\": \"").append(Json.escape(place.name())).append("\",\n");
        out.append("  \"latitude\": ").append(place.latitude()).append(",\n");
        out.append("  \"longitude\": ").append(place.longitude()).append(",\n");
        out.append("  \"timezone\": \"").append(Json.escape(zone.getId())).append("\",\n");
        out.append("  \"date\": \"").append(day.date()).append("\",\n");
        out.append("  \"sunrise\": \"").append(fmt(day.sunrise(), zone)).append("\",\n");
        out.append("  \"sunset\": \"").append(fmt(day.sunset(), zone)).append("\",\n");
        out.append("  \"solar_noon\": \"").append(fmt(day.solarNoon(), zone)).append("\",\n");
        out.append("  \"day_length_minutes\": ").append(day.dayLength().toMinutes()).append(",\n");
        out.append("  \"windows\": [\n");
        for (int i = 0; i < windows.size(); i++) {
            Windows.Window w = windows.get(i);
            out.append("    {\"kind\": \"").append(w.kind()).append("\", \"label\": \"")
                    .append(Json.escape(w.label())).append("\", \"start\": \"")
                    .append(w.start().format(TIME)).append("\", \"end\": \"")
                    .append(w.end().format(TIME)).append("\", \"quality\": ")
                    .append(w.quality() == null ? "null" : w.quality())
                    .append(", \"verdict\": \"").append(Json.escape(w.verdict())).append("\"}");
            out.append(i + 1 < windows.size() ? ",\n" : "\n");
        }
        out.append("  ]\n}");
        return out.toString();
    }

    private static String fmt(java.time.Instant instant, ZoneId zone) {
        return LocalDateTime.ofInstant(instant, zone).format(TIME);
    }

    private static String qualityText(Windows.Window w) {
        if (w.quality() == null) {
            return "no cloud data";
        }
        return w.quality() + "/100 " + w.verdict();
    }
}
