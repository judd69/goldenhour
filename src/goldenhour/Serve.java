package goldenhour;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serves the GoldenHour dashboard ({@code docs/index.html}) on localhost.
 * The page itself fetches live sun/cloud data straight from the two APIs,
 * exactly as the GitHub Pages deployment does — this server only serves
 * the static file. If no docs/ page is found next to the working
 * directory, a minimal inline page is served instead.
 */
public final class Serve {

    private Serve() {
    }

    private static final String FALLBACK_PAGE = """
            <!doctype html><meta charset="utf-8"><title>GoldenHour</title>
            <body style="font-family:system-ui;background:#0B0B16;color:#F5F1E8;padding:2rem">
            <h1>GoldenHour</h1>
            <p>docs/index.html was not found next to this server. Run from the
            repository root, or open the live dashboard at
            <a href="https://judd69.github.io/goldenhour/" style="color:#F59E0B">
            judd69.github.io/goldenhour/</a>.</p></body>
            """;

    public static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        Path docs = Path.of("docs", "index.html");
        byte[] page = Files.exists(docs)
                ? Files.readAllBytes(docs)
                : FALLBACK_PAGE.getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            byte[] body = page;
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.setExecutor(null);
        server.start();
        return server;
    }
}
