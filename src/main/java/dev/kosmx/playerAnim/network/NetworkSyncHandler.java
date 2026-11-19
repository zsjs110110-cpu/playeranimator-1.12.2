package dev.kosmx.playerAnim.network;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Sync hooks for the animation channel.
 * <p>
 * Registered on the Forge event bus from {@code PlayerAnimatorMod#preInit}. Server-side events
 * catch players up with animations that started before they joined, entered the dimension, or
 * walked into tracking range; the client tick reports a one-shot clip finishing so the server
 * can drop its tracked state.
 */
public final class NetworkSyncHandler {

    public static final NetworkSyncHandler INSTANCE = new NetworkSyncHandler();

    private NetworkSyncHandler() {
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (player instanceof EntityPlayerMP) {
            PlayerAnimatorNetwork.resendToObserver((EntityPlayerMP) player);
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        EntityPlayer player = event.player;
        if (player instanceof EntityPlayerMP) {
            PlayerAnimatorNetwork.resendToObserver((EntityPlayerMP) player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayerAnimatorNetwork.clearSource(event.player.getUniqueID());
    }

    /**
     * An observer just started tracking the source entity (e.g. walked into tracking range).
     * Re-send the source's active animation to this observer specifically.
     */
    @SubscribeEvent
    public void onStartTracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        if (!(event.getTarget() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP observer = (EntityPlayerMP) event.getEntityPlayer();
        EntityPlayerMP source = (EntityPlayerMP) event.getTarget();
        PlayerAnimatorNetwork.resendToObserverForSource(observer, source);
    }

    /**
     * Client side: detect when a one-shot network animation finishes and tell the server to drop
     * the tracked state. Server players are not {@link AbstractClientPlayer}, so this stays a
     * client-only check.
     */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof AbstractClientPlayer) {
            ClientAnimationControl.checkFinished((AbstractClientPlayer) event.player);
        }
    }
}
