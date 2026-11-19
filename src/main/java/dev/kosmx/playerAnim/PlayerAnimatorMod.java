package dev.kosmx.playerAnim;

import dev.kosmx.playerAnim.config.PlayerAnimatorConfig;
import dev.kosmx.playerAnim.network.ClientAnimationControl;
import dev.kosmx.playerAnim.network.NetworkSyncHandler;
import dev.kosmx.playerAnim.network.PlayerAnimatorNetwork;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 1.12.2 entry point.
 * <p>
 * Animations are still evaluated by every client for all visible players, but the mod itself loads
 * on both sides now: a server only registers the optional animation channel, while the mixins and
 * every rendering class stay client-only (see {@code PlayerAnimatorMixinLoader}).
 */
@Mod(
        modid = PlayerAnimatorMod.MOD_ID,
        name = "Player Animator",
        version = Tags.VERSION,
        acceptableRemoteVersions = "*"
)
public class PlayerAnimatorMod {

    public static final String MOD_ID = "playeranimator";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public PlayerAnimatorMod() {
        LOGGER.info("Player Animator {} initialized", Tags.VERSION);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        PlayerAnimatorConfig.load(event.getModConfigurationDirectory());
        PlayerAnimatorNetwork.register();
        MinecraftForge.EVENT_BUS.register(NetworkSyncHandler.INSTANCE);
        if (event.getSide().isClient()) {
            // Rendering-side setup: the extra layer the network packets animate.
            ClientAnimationControl.registerFactory();
        }
    }
}
