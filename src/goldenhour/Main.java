package goldenhour;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * GoldenHour CLI — golden-hour / blue-hour planner.
 *
 * <pre>
 *   java -jar build/goldenhour.jar                          today in Mumbai
 *   java -jar build/goldenhour.jar today --city Jaipur
 *   java -jar build/goldenhour.jar today --lat 19.07 --lon 72.87 --date 2026-11-12
 *   java -jar build/goldenhour.jar week [--city NAME]
 *   java -jar build/goldenhour.jar cities
 *   java -jar build/goldenhour.jar --json today --city Delhi
 *   java -jar build/goldenhour.jar --from-file examples today
 *   java -jar build/goldenhour.jar --from-file examples week
 *   java -jar build/goldenhour.jar --serve [port]
 *   java -jar build/goldenhour.jar --help | --version
 * </pre>
 *
 * Exit codes: 0 ok, 1 data / API error, 2 usage error. GoldenHour never
 * invents sun times or cloud readings: if an API cannot be reached and no
 * {@code --from-file} snapshot directory was given, it prints a clear
 * error and exits 1.
 */
public final class Main {

    public static final String VERSION = "1.0.0";

    private static final String HELP = """
            GoldenHour — golden-hour / blue-hour planner via sunrise-sunset.org + Open-Meteo.
            Usage: java -jar build/goldenhour.jar [options] [command]
              (no command)              today's windows for Mumbai (the default place)
              today [--city NAME]       today's windows for a preset place
              today --lat L --lon O     today's windows for custom coordinates
                [--date YYYY-MM-DD]     any date (default today)
                [--tz ZONE]             timezone for custom coordinates (default Asia/Kolkata)
              week [--city NAME]        7-day planner: best window per day
              cities                    list the preset places
              --json                    machine-readable JSON output (today command)
              --from-file DIR           replay saved snapshots in DIR instead of the network
              --serve [port]            serve the dashboard on localhost (default 8080)
              --help                    this help
              --version                 print the version
            Golden hour = sunrise to +60 min, and sunset -60 min to sunset.
            Blue hour = civil twilight to sunrise, and sunset to civil twilight end.
            Quality (0-100) is an estimate from cloud cover only — not haze or pollution.
            Exit codes: 0 ok, 1 data/API error, 2 usage error.""";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        System.exit(run(args, System.out, System.err));
    }

    /** Runs the CLI; returns the process exit code (testable without System.exit). */
    public static int run(String[] args, PrintStream out, PrintStream err) {
        boolean json = false;
        boolean serve = false;
        int servePort = 8080;
        Path fromFile = null;
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    out.println(HELP);
                    return 0;
                }
                case "--version", "-v" -> {
                    out.println("goldenhour " + VERSION);
                    return 0;
                }
                case "--json" -> json = true;
                case "--serve" -> {
                    serve = true;
                    if (i + 1 < args.length && args[i + 1].matches("\\d+")) {
                        servePort = Integer.parseInt(args[++i]);
                    }
                }
                case "--from-file" -> {
                    if (i + 1 >= args.length) {
                        err.println("error: --from-file needs a directory path");
                        return 2;
                    }
                    fromFile = Path.of(args[++i]);
                }
                // Subcommand flags belong to today/week; pass them through.
                case "--city", "--lat", "--lon", "--date", "--tz" -> positional.add(args[i]);
                default -> {
                    if (args[i].startsWith("--")) {
                        err.println("error: unknown option '" + args[i] + "' — see --help");
                        return 2;
                    }
                    positional.add(args[i]);
                }
            }
        }

        try {
            if (serve) {
                return serve(servePort, out, err);
            }
            if (positional.isEmpty()) {
                return today(new Options(), fromFile, json, out, err);
            }
            switch (positional.get(0)) {
                case "today" -> {
                    return today(Options.parse(positional.subList(1, positional.size())), fromFile, json, out, err);
                }
                case "week" -> {
                    return week(Options.parse(positional.subList(1, positional.size())), fromFile, out, err);
                }
                case "cities" -> {
                    out.print(Report.citiesText());
                    return 0;
                }
                default -> {
                    err.println("error: unknown command '" + positional.get(0) + "' — see --help");
                    return 2;
                }
            }
        } catch (Options.UsageException e) {
            err.println("error: " + e.getMessage());
            return 2;
        } catch (SunClient.DataException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }
    }

    /** Parsed options for today/week. */
    static final class Options {
        String city;
        Double lat;
        Double lon;
        LocalDate date;
        String tz;

        static final class UsageException extends Exception {
            UsageException(String message) {
                super(message);
            }
        }

        static Options parse(List<String> tokens) throws UsageException {
            Options o = new Options();
            for (int i = 0; i < tokens.size(); i++) {
                String flag = tokens.get(i);
                if (i + 1 >= tokens.size()) {
                    throw new UsageException(flag + " needs a value");
                }
                String value = tokens.get(++i);
                switch (flag) {
                    case "--city" -> o.city = value;
                    case "--lat" -> o.lat = parseDouble(flag, value);
                    case "--lon" -> o.lon = parseDouble(flag, value);
                    case "--date" -> {
                        try {
                            o.date = LocalDate.parse(value);
                        } catch (java.time.format.DateTimeParseException e) {
                            throw new UsageException("--date needs YYYY-MM-DD, got '" + value + "'");
                        }
                    }
                    case "--tz" -> o.tz = value;
                    default -> throw new UsageException("unknown option '" + flag + "' — see --help");
                }
            }
            if (o.city != null && (o.lat != null || o.lon != null)) {
                throw new UsageException("use --city or --lat/--lon, not both");
            }
            if ((o.lat == null) != (o.lon == null)) {
                throw new UsageException("--lat and --lon go together");
            }
            if (o.lat != null && (o.lat < -90 || o.lat > 90)) {
                throw new UsageException("--lat must be between -90 and 90");
            }
            if (o.lon != null && (o.lon < -180 || o.lon > 180)) {
                throw new UsageException("--lon must be between -180 and 180");
            }
            if (o.tz != null) {
                try {
                    ZoneId.of(o.tz);
                } catch (java.time.DateTimeException e) {
                    throw new UsageException("unknown timezone '" + o.tz + "'");
                }
            }
            return o;
        }

        private static double parseDouble(String flag, String value) throws UsageException {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                throw new UsageException(flag + " needs a number, got '" + value + "'");
            }
        }
    }

    /** Resolves the place: preset city, custom coordinates, or the Mumbai default. */
    private static Cities.City resolvePlace(Options o) throws Options.UsageException {
        if (o.city != null) {
            return Cities.find(o.city)
                    .orElseThrow(() -> new Options.UsageException(
                            "unknown place '" + o.city + "' — run 'cities' for the preset list"));
        }
        if (o.lat != null) {
            ZoneId zone = o.tz == null ? ZoneId.of("Asia/Kolkata") : ZoneId.of(o.tz);
            return new Cities.City(
                    String.format(java.util.Locale.ROOT, "%.4f, %.4f", o.lat, o.lon),
                    o.lat, o.lon, zone);
        }
        return Cities.DEFAULT;
    }

    private static int today(Options o, Path fromFile, boolean json,
                             PrintStream out, PrintStream err) throws SunClient.DataException {
        Cities.City place;
        try {
            place = resolvePlace(o);
        } catch (Options.UsageException e) {
            err.println("error: " + e.getMessage());
            return 2;
        }
        LocalDate date = o.date != null ? o.date : LocalDate.now(place.zone());
        SunData.SunDay day;
        SunData.CloudData cloud;
        if (fromFile != null) {
            List<SunData.SunDay> days = SunClient.sunFromDir(fromFile);
            day = days.stream().filter(d -> d.date().equals(date)).findFirst()
                    .orElseThrow(() -> new SunClient.DataException(
                            "No snapshot for " + date + " in " + fromFile
                                    + " (snapshots cover " + days.get(0).date()
                                    + " to " + days.get(days.size() - 1).date() + ")"));
            cloud = SunClient.cloudFromDir(fromFile);
        } else {
            SunClient client = new SunClient();
            day = client.sunDay(place.latitude(), place.longitude(), date);
            cloud = client.cloudWeek(place.latitude(), place.longitude(), place.zone().getId());
        }
        List<Windows.Window> windows = Windows.windowsFor(day, cloud, place.zone());
        if (json) {
            out.println(Report.todayJson(place, day, windows, place.zone()));
        } else {
            out.print(Report.todayText(place, day, windows, place.zone()));
        }
        return 0;
    }

    private static int week(Options o, Path fromFile,
                            PrintStream out, PrintStream err) throws SunClient.DataException {
        Cities.City place;
        try {
            place = resolvePlace(o);
        } catch (Options.UsageException e) {
            err.println("error: " + e.getMessage());
            return 2;
        }
        LocalDate start = o.date != null ? o.date : LocalDate.now(place.zone());
        List<SunData.SunDay> days;
        SunData.CloudData cloud;
        if (fromFile != null) {
            days = SunClient.sunFromDir(fromFile);
            if (!days.get(0).date().equals(start)) {
                throw new SunClient.DataException(
                        "Snapshots start " + days.get(0).date() + ", not " + start
                                + " — use --date " + days.get(0).date() + " or fetch live data");
            }
            cloud = SunClient.cloudFromDir(fromFile);
        } else {
            SunClient client = new SunClient();
            days = client.sunWeek(place.latitude(), place.longitude(), start);
            cloud = client.cloudWeek(place.latitude(), place.longitude(), place.zone().getId());
        }
        out.print(Report.weekText(place, days, cloud, place.zone()));
        return 0;
    }

    private static int serve(int port, PrintStream out, PrintStream err) {
        try {
            HttpServer server = Serve.start(port);
            out.println("GoldenHour dashboard at http://localhost:" + port + "/ (Ctrl+C to stop)");
            Object lock = new Object();
            synchronized (lock) {
                lock.wait();
            }
            server.stop(0);
            return 0;
        } catch (IOException e) {
            err.println("error: could not start the server on port " + port + ": " + e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }
    }
}
