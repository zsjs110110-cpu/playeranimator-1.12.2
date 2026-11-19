package dev.kosmx.playerAnim.network;

import dev.kosmx.playerAnim.PlayerAnimatorMod;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client side of the network layer: owns one extra animation layer per player and plays the clip
 * a {@link MessagePlayAnimation} asked for.
 * <p>
 * The layer is registered as a factory so it exists for every player from the moment the stack
 * is built; a player that somehow missed the factory gets one attached on first use.
 */
@SideOnly(Side.CLIENT)
public final class ClientAnimationControl {

    /** Layer id in the player's associated data. */
    public static final ResourceLocation LAYER_ID = new ResourceLocation(PlayerAnimatorMod.MOD_ID, "network");

    /** Above the library defaults (0..3), below anything a mod wants to put on top. */
    private static final int LAYER_PRIORITY = 100;

    private static final Logger LOGGER = LogManager.getLogger(PlayerAnimatorMod.MOD_ID);
    private static final Set<ResourceLocation> missingReported = new HashSet<>();

    /** Last tick's active state per player, used to detect a one-shot clip finishing. */
    private static final Map<UUID, Boolean> wasActive = new HashMap<>();

    private static boolean factoryRegistered;

    private ClientAnimationControl() {
    }

    /** Called once from preInit on the client. */
    public static void registerFactory() {
        if (factoryRegistered) {
            return;
        }
        factoryRegistered = true;
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                LAYER_ID, LAYER_PRIORITY, player -> new ModifierLayer<>()
        );
    }

    public static void play(UUID playerId, ResourceLocation animationId, int fadeIn) {
        if (playerId == null || animationId == null) {
            return;
        }
        AbstractClientPlayer player = findPlayer(playerId);
        if (player == null) {
            return;
        }
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(animationId);
        if (animation == null) {
            // Not an error: the server may ship a clip this client's packs do not have.
            if (missingReported.add(animationId)) {
                LOGGER.warn("Ignoring network animation {}: not registered on this client", animationId);
            }
            return;
        }
        ModifierLayer<IAnimation> layer = layerFor(player);
        if (layer == null) {
            return;
        }
        // fadeFromNothing=true: fade in from the default pose even when the layer was empty
        // (the two-arg overload skips the fade modifier when there is no active animation).
        layer.replaceAnimationWithFade(fade(fadeIn), new KeyframeAnimationPlayer(animation), true);
    }

    public static void stop(UUID playerId, int fadeOut) {
        if (playerId == null) {
            return;
        }
        AbstractClientPlayer player = findPlayer(playerId);
        if (player == null) {
            return;
        }
        ModifierLayer<IAnimation> layer = layerFor(player);
        if (layer == null) {
            return;
        }
        layer.replaceAnimationWithFade(fade(fadeOut), null);
    }

    /**
     * Called every client tick: report to the server when a one-shot network animation stops,
     * so it can drop the tracked state. Looping clips stay active and never report finished.
     */
    static void checkFinished(AbstractClientPlayer player) {
        UUID id = player.getUniqueID();
        ModifierLayer<IAnimation> layer = layerFor(player);
        IAnimation animation = layer == null ? null : layer.getAnimation();
        boolean active = animation != null && animation.isActive();
        Boolean previous = wasActive.get(id);
        if (Boolean.TRUE.equals(previous) && !active) {
            PlayerAnimatorNetwork.sendAnimationFinished(id);
        }
        wasActive.put(id, active);
    }

    private static AbstractClientPlayer findPlayer(UUID playerId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) {
            return null;
        }
        EntityPlayer player = minecraft.world.getPlayerEntityByUUID(playerId);
        return player instanceof AbstractClientPlayer ? (AbstractClientPlayer) player : null;
    }

    @SuppressWarnings("unchecked")
    private static ModifierLayer<IAnimation> layerFor(AbstractClientPlayer player) {
        IAnimation existing = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
        if (existing instanceof ModifierLayer) {
            return (ModifierLayer<IAnimation>) existing;
        }
        // Late player (stack built before the factory was registered): attach one now.
        ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(LAYER_PRIORITY, layer);
        PlayerAnimationAccess.getPlayerAssociatedData(player).set(LAYER_ID, layer);
        return layer;
    }

    private static AbstractFadeModifier fade(int ticks) {
        return AbstractFadeModifier.standardFadeIn(Math.max(1, ticks), Ease.INOUTSINE);
    }
}
