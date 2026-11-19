package dev.kosmx.playerAnim.network;

import dev.kosmx.playerAnim.PlayerAnimatorMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server to client animation packets.
 * <p>
 * The library still evaluates animations on the client (see the README); this channel only
 * carries "play this clip on that player" hints for cases where the server is the only side
 * that knows something happened. Packets are only sent when the API below is called, so a
 * server with the mod installed stays silent unless a mod asks for it.
 * <p>
 * The receiving side resolves the id through {@link dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry}
 * and drops it if the clip is not registered there, so the payload stays tiny (a resource location).
 */
public final class PlayerAnimatorNetwork {

    private static final byte ID_PLAY = 0;
    private static final byte ID_STOP = 1;
    private static final byte ID_FINISHED = 2;

    private static final Logger LOGGER = LogManager.getLogger(PlayerAnimatorMod.MOD_ID);
    private static final SimpleNetworkWrapper WRAPPER = NetworkRegistry.INSTANCE.newSimpleChannel(PlayerAnimatorMod.MOD_ID);
    private static boolean registered;

    /**
     * Animations currently driven through {@link #playAnimationForObservers}, keyed by the
     * source player's UUID. Kept so a player who joins, changes dimension, or starts tracking
     * the source after the animation began can be caught up with what is already playing.
     * Server-side state only.
     */
    private static final Map<UUID, ActiveAnimation> ACTIVE_ANIMATIONS = new ConcurrentHashMap<>();

    private PlayerAnimatorNetwork() {
    }

    /** Called from preInit, on both sides. Safe to call twice. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        WRAPPER.registerMessage(PlayAnimationHandler.class, MessagePlayAnimation.class, ID_PLAY, Side.CLIENT);
        WRAPPER.registerMessage(StopAnimationHandler.class, MessageStopAnimation.class, ID_STOP, Side.CLIENT);
        WRAPPER.registerMessage(FinishedAnimationHandler.class, MessageAnimationFinished.class, ID_FINISHED, Side.SERVER);
        LOGGER.info("Player Animator network channel '{}' registered", PlayerAnimatorMod.MOD_ID);
    }

    // ------------------------------------------------------------------ server API

    /**
     * Make the given player's own client play a clip.
     *
     * @param target      player whose client should animate
     * @param animationId clip id registered in {@code PlayerAnimationRegistry} on that client
     * @param fadeIn      fade-in length in ticks (8 matches the upstream default)
     */
    public static void playAnimation(EntityPlayerMP target, ResourceLocation animationId, int fadeIn) {
        if (target == null || animationId == null) {
            return;
        }
        WRAPPER.sendTo(new MessagePlayAnimation(target.getUniqueID(), animationId, fadeIn), target);
    }

    /**
     * Tell every client that can see {@code source} to animate the source player. Use this for
     * animations a third person would notice; it is a dimension broadcast, which is fine for a
     * cosmetic packet and avoids depending on a chunk map.
     */
    public static void playAnimationForObservers(EntityPlayer source, ResourceLocation animationId, int fadeIn) {
        if (source == null || animationId == null) {
            return;
        }
        ACTIVE_ANIMATIONS.put(source.getUniqueID(), new ActiveAnimation(animationId, fadeIn));
        sendToObservers(source, new MessagePlayAnimation(source.getUniqueID(), animationId, fadeIn));
    }

    /** Stop the network animation on the target's client, fading out over {@code fadeOut} ticks. */
    public static void stopAnimation(EntityPlayerMP target, int fadeOut) {
        if (target == null) {
            return;
        }
        WRAPPER.sendTo(new MessageStopAnimation(target.getUniqueID(), fadeOut), target);
    }

    /** Same as {@link #stopAnimation}, but for everyone watching the player. */
    public static void stopAnimationForObservers(EntityPlayer source, int fadeOut) {
        if (source == null) {
            return;
        }
        ACTIVE_ANIMATIONS.remove(source.getUniqueID());
        sendToObservers(source, new MessageStopAnimation(source.getUniqueID(), fadeOut));
    }

    // ------------------------------------------------------------ sync / catch-up

    /**
     * Re-send every animation currently playing for observers in the observer's dimension.
     * Called when a player joins or changes dimension, so they do not miss a clip that started
     * before they could see the source player.
     */
    static void resendToObserver(EntityPlayerMP observer) {
        if (observer == null) {
            return;
        }
        MinecraftServer server = observer.getServer();
        if (server == null) {
            return;
        }
        for (Map.Entry<UUID, ActiveAnimation> entry : ACTIVE_ANIMATIONS.entrySet()) {
            EntityPlayerMP source = server.getPlayerList().getPlayerByUUID(entry.getKey());
            if (source == null || source.dimension != observer.dimension) {
                continue;
            }
            ActiveAnimation active = entry.getValue();
            WRAPPER.sendTo(
                    new MessagePlayAnimation(source.getUniqueID(), active.animationId, active.fadeIn),
                    observer
            );
        }
    }

    /**
     * Re-send the source player's active animation to one observer (called when that observer
     * starts tracking the source, e.g. walking into tracking range).
     */
    static void resendToObserverForSource(EntityPlayerMP observer, EntityPlayerMP source) {
        if (observer == null || source == null) {
            return;
        }
        ActiveAnimation active = ACTIVE_ANIMATIONS.get(source.getUniqueID());
        if (active == null) {
            return;
        }
        WRAPPER.sendTo(
                new MessagePlayAnimation(source.getUniqueID(), active.animationId, active.fadeIn),
                observer
        );
    }

    /** Tell the server this player's network animation has finished and its state can be dropped. */
    public static void sendAnimationFinished(UUID sourceId) {
        if (sourceId != null) {
            WRAPPER.sendToServer(new MessageAnimationFinished(sourceId));
        }
    }

    /** Drop a source player's tracked animation (called from the finished packet or on logout). */
    static void clearSource(UUID sourceId) {
        if (sourceId != null) {
            ACTIVE_ANIMATIONS.remove(sourceId);
        }
    }

    private static void sendToObservers(EntityPlayer source, IMessage message) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            // Client-side world (single player without an integrated server call path): nothing to do.
            return;
        }
        for (EntityPlayerMP observer : server.getPlayerList().getPlayers()) {
            if (observer.dimension == source.dimension) {
                WRAPPER.sendTo(message, observer);
            }
        }
    }

    /** Server-side record of an animation currently playing for observers. */
    private static final class ActiveAnimation {
        private final ResourceLocation animationId;
        private final int fadeIn;

        private ActiveAnimation(ResourceLocation animationId, int fadeIn) {
            this.animationId = animationId;
            this.fadeIn = fadeIn;
        }
    }
}
