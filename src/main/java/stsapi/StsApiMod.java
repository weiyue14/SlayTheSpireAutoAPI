package stsapi;

import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ModTheSpire entry point. Starts a local HTTP server that exposes the live
 * game state (combat, card piles and true draw order, monsters and intents,
 * potions, HP, relics, map) as JSON for external automation tools.
 *
 * State capture is read-only; legal actions can optionally be queued via HTTP.
 */
@SpireInitializer
public class StsApiMod {

    public static final String VERSION = "1.4.0";
    public static final Logger logger = LogManager.getLogger(StsApiMod.class.getName());

    public static void initialize() {
        logger.info("SlayTheSpire Auto API v" + VERSION + " initializing");
        try {
            Config cfg = Config.load();
            ApiServer.start(cfg);
        } catch (Throwable t) {
            // Never crash the game because of the API server.
            logger.error("SlayTheSpire Auto API: failed to start HTTP server", t);
        }
    }
}
