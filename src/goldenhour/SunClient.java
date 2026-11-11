package goldenhour;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * Clients for the two free, keyless, CORS-enabled APIs GoldenHour uses:
 * sunrise-sunset.org (sun times) and Open-Meteo (hourly cloud cover).
 *
 * <p>GoldenHour never invents sun times or cloud readings: if the network
 * fails and no {@code --from-file} snapshot was given, the caller gets a
 * {@link DataException} and the CLI exits 1 with a clear message.
 */
public final class SunClient {

    public static final String SUN_BASE = "https://api.sunrise-sunset.org/json";
    public static final String CLOUD_BASE = "https://api.open-meteo.com/v1/forecast";

    private final HttpClient http;

    public SunClient() {
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Thrown when sun/cloud data cannot be obtained (network down, bad response, bad snapshot). */
    public static final class DataException extends Exception {
        public DataException(String message) {
            super(message);
        }

        public DataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** One day of sun times, live from sunrise-sunset.org. */
    public SunData.SunDay sunDay(double lat, double lon, LocalDate date) throws DataException {
        String url = SUN_BASE + "?lat=" + lat + "&lng=" + lon + "&date=" + date + "&formatted=0";
        String body = get(url, "sunrise-sunset.org");
        try {
            return SunData.parseSunDay(body, date);
        } catch (IllegalArgumentException e) {
            throw new DataException("Could not parse the sunrise-sunset.org response: " + e.getMessage(), e);
        }
    }

    /** Seven days of sun times (today + 6), one request per day. */
    public List<SunData.SunDay> sunWeek(double lat, double lon, LocalDate start) throws DataException {
        var out = new java.util.ArrayList<SunData.SunDay>();
        for (int i = 0; i < 7; i++) {
            out.add(sunDay(lat, lon, start.plusDays(i)));
        }
        return out;
    }

    /** Seven days of hourly cloud cover, live from Open-Meteo. */
    public SunData.CloudData cloudWeek(double lat, double lon, String timezone) throws DataException {
        String url = CLOUD_BASE + "?latitude=" + lat + "&longitude=" + lon
                + "&hourly=cloud_cover&forecast_days=7&timezone="
                + URLEncoder.encode(timezone, StandardCharsets.UTF_8);
        String body = get(url, "api.open-meteo.com");
        try {
            return SunData.parseCloud(body);
        } catch (IllegalArgumentException e) {
            throw new DataException("Could not parse the Open-Meteo response: " + e.getMessage(), e);
        }
    }

    // ---- snapshots -------------------------------------------------------

    /** Reads a saved JSON snapshot from disk (verbatim API response). */
    public static String readSnapshot(Path path) throws DataException {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new DataException("Cannot read snapshot file " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Loads sun data from a directory of snapshots: a file starting with
     * {@code sun} — either the 7-day {@code sun-*-7d.json} week file or a
     * single-day sunrise-sunset.org response. Returns the days found.
     */
    public static List<SunData.SunDay> sunFromDir(Path dir) throws DataException {
        List<Path> candidates = listSnapshots(dir, "sun");
        Path singleDay = null;
        for (Path candidate : candidates) {
            String body = readSnapshot(candidate);
            if (body.contains("\"days\"")) {
                try {
                    return SunData.parseSunWeek(body);
                } catch (IllegalArgumentException e) {
                    throw new DataException("Snapshot " + candidate
                            + " is not a sunrise-sunset.org week response: " + e.getMessage(), e);
                }
            }
            if (singleDay == null) {
                singleDay = candidate;
            }
        }
        if (singleDay == null) {
            throw new DataException("No sun*.json snapshot in " + dir);
        }
        String body = readSnapshot(singleDay);
        try {
            LocalDate date = dateFromName(singleDay.getFileName().toString());
            return List.of(SunData.parseSunDay(body, date));
        } catch (IllegalArgumentException e) {
            throw new DataException("Snapshot " + singleDay + " is not a sunrise-sunset.org response: "
                    + e.getMessage(), e);
        }
    }

    /** Loads cloud data from a directory of snapshots (a file starting with {@code cloud}). */
    public static SunData.CloudData cloudFromDir(Path dir) throws DataException {
        Path cloudFile = findSnapshot(dir, "cloud");
        try {
            return SunData.parseCloud(readSnapshot(cloudFile));
        } catch (IllegalArgumentException e) {
            throw new DataException("Snapshot " + cloudFile + " is not an Open-Meteo response: "
                    + e.getMessage(), e);
        }
    }

    private static Path findSnapshot(Path dir, String prefix) throws DataException {
        List<Path> found = listSnapshots(dir, prefix);
        if (found.isEmpty()) {
            throw new DataException("No " + prefix + "*.json snapshot in " + dir);
        }
        return found.get(0);
    }

    private static List<Path> listSnapshots(Path dir, String prefix) throws DataException {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix)
                            && p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new DataException("Cannot list snapshot directory " + dir + ": " + e.getMessage(), e);
        }
    }

    /** Extracts the date from a snapshot name like {@code sun-mumbai-2026-11-11.json}. */
    private static LocalDate dateFromName(String name) throws DataException {
        var m = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(name);
        if (!m.find()) {
            throw new DataException("Snapshot name " + name + " carries no YYYY-MM-DD date");
        }
        return LocalDate.parse(m.group(1));
    }

    private String get(String url, String host) throws DataException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DataException(
                    "Could not reach " + host + " (" + e.getMessage() + "). "
                            + "Check your connection, or replay saved snapshots with --from-file. "
                            + "GoldenHour never shows invented sun times.", e);
        }
        if (response.statusCode() != 200) {
            throw new DataException(host + " returned HTTP " + response.statusCode()
                    + " for " + url + ". Try again later, or replay saved snapshots with --from-file.");
        }
        return response.body();
    }
}
