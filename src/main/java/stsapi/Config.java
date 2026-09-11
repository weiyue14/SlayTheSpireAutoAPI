package stsapi;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * API server settings. Priority: env vars > config file > defaults.
 * Config file: mods/SlayTheSpireAutoAPI.cfg (relative to the game root,
 * which is the working directory when the game runs through ModTheSpire).
 *
 * Supported keys:
 *   port=8080
 *   host=127.0.0.1     (use 0.0.0.0 to expose to the LAN, not recommended)
 */
final class Config {

    static final int DEFAULT_PORT = 8080;
    static final String DEFAULT_HOST = "127.0.0.1";
    static final String CONFIG_FILE = "mods/SlayTheSpireAutoAPI.cfg";

    int port = DEFAULT_PORT;
    String host = DEFAULT_HOST;
    /** When false, POST /api/action returns 403 (read-only mode). */
    boolean actions = true;

    private Config() {
    }

    static Config load() {
        Config c = new Config();
        try {
            Path p = Paths.get(CONFIG_FILE);
            if (Files.exists(p)) {
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eq = line.indexOf('=');
                    if (eq <= 0) continue;
                    String key = line.substring(0, eq).trim().toLowerCase();
                    String val = line.substring(eq + 1).trim();
                    if ("port".equals(key)) {
                        c.port = Integer.parseInt(val);
                    } else if ("host".equals(key)) {
                        c.host = val;
                    } else if ("actions".equals(key)) {
                        c.actions = !"false".equalsIgnoreCase(val) && !"0".equals(val);
                    }
                }
            }
        } catch (Exception ignored) {
            // fall back to defaults
        }
        String envPort = System.getenv("STSAPI_PORT");
        if (envPort != null && !envPort.trim().isEmpty()) {
            try {
                c.port = Integer.parseInt(envPort.trim());
            } catch (Exception ignored) {
            }
        }
        String envHost = System.getenv("STSAPI_HOST");
        if (envHost != null && !envHost.trim().isEmpty()) {
            c.host = envHost.trim();
        }
        return c;
    }
}
