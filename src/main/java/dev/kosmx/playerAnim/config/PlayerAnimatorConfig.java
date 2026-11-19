package dev.kosmx.playerAnim.config;

import dev.kosmx.playerAnim.PlayerAnimatorMod;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Forge config for the 1.12.2 port, plus the cached flags the render hot path reads.
 * <p>
 * Everything here used to be a {@code Boolean.getBoolean(...)} call inside
 * {@code ModelBiped#setRotationAngles} / {@code RenderPlayer#applyRotations}, which runs for
 * every biped model (main model + one per armor piece) on every frame for every visible player.
 * The values are read once at preInit and then served from fields; the old JVM properties still
 * win if they are set, so existing debug command lines keep working.
 */
public final class PlayerAnimatorConfig {

    public static final String FILE_NAME = "playeranimator.cfg";

    /** JVM property prefix: {@code -Dplayeranimator.bodyAdjust=true}. */
    public static final String PROPERTY_PREFIX = PlayerAnimatorMod.MOD_ID + ".";

    private static final String CATEGORY_GENERAL = "general";
    private static final String CATEGORY_DEBUG = "debug";
    private static final String CATEGORY_NETWORK = "network";

    private static final Logger LOGGER = LogManager.getLogger(PlayerAnimatorMod.MOD_ID);

    private static boolean loaded;

    private static boolean bodyAdjust = false;
    private static boolean debugModel = false;
    private static boolean debugFrames = false;
    private static boolean networkAnimations = true;

    private PlayerAnimatorConfig() {
    }

    public static void load(File configDirectory) {
        Configuration configuration = new Configuration(new File(configDirectory, FILE_NAME));
        try {
            configuration.load();
            bodyAdjust = readBoolean(
                    configuration, "bodyAdjust", CATEGORY_GENERAL, false,
                    "Apply the animation's 'body' position/rotation to the model root matrix, matching upstream. "
                            + "Off by default: a mod that already rotates the root itself (e.g. a look-aligned body) "
                            + "would otherwise get the rotation twice. JVM property -Dplayeranimator.bodyAdjust overrides this."
            );
            debugModel = readBoolean(
                    configuration, "debugModel", CATEGORY_DEBUG, false,
                    "Log the biped part rotations once per second."
            );
            debugFrames = readBoolean(
                    configuration, "debugFrames", CATEGORY_DEBUG, false,
                    "Log the finished pose of every rendered frame. Very spammy, use for short sessions only."
            );
            networkAnimations = readBoolean(
                    configuration, "enable", CATEGORY_NETWORK, true,
                    "Accept 'play this clip' packets from the server on the playeranimator channel. "
                            + "Animations are still evaluated locally; the packet only says which clip to resolve. "
                            + "Turn this off to ignore whatever a server sends. JVM property "
                            + "-Dplayeranimator.networkAnimations overrides this.",
                    "networkAnimations"
            );
        } catch (Throwable throwable) {
            LOGGER.warn("Player Animator config could not be read, falling back to defaults", throwable);
        } finally {
            try {
                configuration.save();
            } catch (Throwable throwable) {
                LOGGER.warn("Player Animator config could not be saved", throwable);
            }
        }
        loaded = true;
        LOGGER.info("Player Animator config: bodyAdjust={}, debugModel={}, debugFrames={}, network.enable={}",
                bodyAdjust, debugModel, debugFrames, networkAnimations);
    }

    private static boolean readBoolean(Configuration configuration, String key, String category,
                                       boolean defaultValue, String comment) {
        return readBoolean(configuration, key, category, defaultValue, comment, key);
    }

    /**
     * @param propertyKey name used for the {@code -Dplayeranimator.*} override; it differs from the
     *                    config key where the file key is shorter than the property (network.enable).
     */
    private static boolean readBoolean(Configuration configuration, String key, String category,
                                       boolean defaultValue, String comment, String propertyKey) {
        boolean value = configuration.getBoolean(key, category, defaultValue, comment);
        String property = System.getProperty(PROPERTY_PREFIX + propertyKey);
        if (property == null) {
            return value;
        }
        boolean fromProperty = Boolean.parseBoolean(property.trim());
        if (fromProperty == value) {
            return value;
        }
        LOGGER.info("Player Animator config {}={} overridden by JVM property to {}", key, value, fromProperty);
        return fromProperty;
    }

    /**
     * Cached value once the config has been loaded; before that (e.g. a unit test or an early
     * render hook) fall back to the JVM property so behaviour is not silently changed.
     */
    private static boolean cachedOrProperty(String key, boolean cached) {
        if (loaded) {
            return cached;
        }
        String property = System.getProperty(PROPERTY_PREFIX + key);
        return property == null ? cached : Boolean.parseBoolean(property.trim());
    }

    /** {@code body} root-matrix transform. Off by default, see the config comment. */
    public static boolean isBodyAdjust() {
        return cachedOrProperty("bodyAdjust", bodyAdjust);
    }

    /** Once-per-second pose dump. */
    public static boolean isDebugModel() {
        return cachedOrProperty("debugModel", debugModel);
    }

    /** Per-frame pose dump. */
    public static boolean isDebugFrames() {
        return cachedOrProperty("debugFrames", debugFrames);
    }

    /** Accept animation packets from the server. */
    public static boolean isNetworkAnimations() {
        return cachedOrProperty("networkAnimations", networkAnimations);
    }
}
