package goldenhour;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The data GoldenHour works with: one day of sun times from
 * sunrise-sunset.org, and hourly cloud cover from Open-Meteo.
 *
 * <p>Sun times arrive as UTC ISO-8601 instants; cloud times arrive as
 * naive local times in the forecast's timezone. Both are kept exactly as
 * the APIs returned them — GoldenHour never invents a sun time or a cloud
 * reading.
 */
public final class SunData {

    private SunData() {
    }

    /** One day of sun times for one place, all instants in UTC. */
    public record SunDay(
            LocalDate date,
            Instant sunrise,
            Instant sunset,
            Instant solarNoon,
            Duration dayLength,
            Instant civilBegin,
            Instant civilEnd,
            Instant nauticalBegin,
            Instant nauticalEnd,
            Instant astroBegin,
            Instant astroEnd) {
    }

    /** Hourly cloud cover for a place: local times in {@link #zone()}. */
    public record CloudData(ZoneId zone, List<LocalDateTime> times, List<Integer> cloudCover) {

        /** Cloud cover (%) at the hour starting at {@code time}, if present. */
        public Integer cloudAt(LocalDateTime time) {
            int i = times.indexOf(time);
            return i < 0 ? null : cloudCover.get(i);
        }

        /**
         * Mean cloud cover (%) over the hours whose hour overlaps the
         * window — an hourly value covers its whole hour, so a 23-minute
         * blue hour still scores from the hour it falls in. Returns null
         * when no hourly value overlaps the window (then the caller must
         * say "no cloud data", not guess).
         */
        public Double meanCloudOver(LocalDateTime start, LocalDateTime end) {
            double sum = 0;
            int n = 0;
            for (int i = 0; i < times.size(); i++) {
                LocalDateTime hourStart = times.get(i);
                LocalDateTime hourEnd = hourStart.plusHours(1);
                if (hourStart.isBefore(end) && hourEnd.isAfter(start)) {
                    sum += cloudCover.get(i);
                    n++;
                }
            }
            return n == 0 ? null : sum / n;
        }
    }

    /** Parses a sunrise-sunset.org response ({@code {"results": {...}, "status": "OK"}}). */
    public static SunDay parseSunDay(String body, LocalDate date) {
        Map<String, Object> root = Json.obj(Json.parse(body));
        Object status = root.get("status");
        if (!"OK".equals(status)) {
            throw new IllegalArgumentException(
                    "sunrise-sunset.org returned status " + status + " (expected OK)");
        }
        Map<String, Object> r = Json.obj(Json.field(root, "results"));
        return new SunDay(
                date,
                instant(r, "sunrise"),
                instant(r, "sunset"),
                instant(r, "solar_noon"),
                Duration.ofSeconds((long) Json.num(Json.field(r, "day_length"))),
                instant(r, "civil_twilight_begin"),
                instant(r, "civil_twilight_end"),
                instant(r, "nautical_twilight_begin"),
                instant(r, "nautical_twilight_end"),
                instant(r, "astronomical_twilight_begin"),
                instant(r, "astronomical_twilight_end"));
    }

    /** Parses the 7-day snapshot format GoldenHour saves: {@code {"days": [{date, results}]}}. */
    public static List<SunDay> parseSunWeek(String body) {
        Map<String, Object> root = Json.obj(Json.parse(body));
        Object days = Json.field(root, "days");
        if (!(days instanceof List<?> list)) {
            throw new IllegalArgumentException("Expected a JSON array under 'days'");
        }
        List<SunDay> out = new ArrayList<>();
        for (Object entry : list) {
            Map<String, Object> day = Json.obj(entry);
            LocalDate date = LocalDate.parse(Json.str(Json.field(day, "date")));
            Map<String, Object> r = Json.obj(Json.field(day, "results"));
            out.add(new SunDay(
                    date,
                    instant(r, "sunrise"),
                    instant(r, "sunset"),
                    instant(r, "solar_noon"),
                    Duration.ofSeconds((long) Json.num(Json.field(r, "day_length"))),
                    instant(r, "civil_twilight_begin"),
                    instant(r, "civil_twilight_end"),
                    instant(r, "nautical_twilight_begin"),
                    instant(r, "nautical_twilight_end"),
                    instant(r, "astronomical_twilight_begin"),
                    instant(r, "astronomical_twilight_end")));
        }
        return out;
    }

    /** Parses an Open-Meteo hourly forecast ({@code hourly.time} + {@code hourly.cloud_cover}). */
    public static CloudData parseCloud(String body) {
        Map<String, Object> root = Json.obj(Json.parse(body));
        ZoneId zone = ZoneId.of(Json.str(Json.field(root, "timezone")));
        Map<String, Object> hourly = Json.obj(Json.field(root, "hourly"));
        List<?> times = list(Json.field(hourly, "time"));
        List<?> cloud = list(Json.field(hourly, "cloud_cover"));
        if (times.size() != cloud.size()) {
            throw new IllegalArgumentException(
                    "Open-Meteo time/cloud_cover arrays differ in length");
        }
        List<LocalDateTime> parsedTimes = new ArrayList<>();
        List<Integer> parsedCloud = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) {
            parsedTimes.add(LocalDateTime.parse(Json.str(times.get(i))));
            parsedCloud.add((int) Json.num(cloud.get(i)));
        }
        return new CloudData(zone, parsedTimes, parsedCloud);
    }

    private static Instant instant(Map<String, Object> r, String key) {
        return Instant.parse(Json.str(Json.field(r, key)));
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Expected a JSON array");
        }
        return (List<Object>) value;
    }
}
