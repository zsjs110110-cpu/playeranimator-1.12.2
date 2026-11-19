package dev.kosmx.playerAnim.network;

import dev.kosmx.playerAnim.config.PlayerAnimatorConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Runs on the client only (the message is registered with {@code Side.CLIENT}).
 * <p>
 * The handler body is dispatched to the client thread: in 1.12.2 {@code onMessage} runs on the
 * netty thread, and touching the world or animation stacks from there is a data race.
 */
public class PlayAnimationHandler implements IMessageHandler<MessagePlayAnimation, IMessage> {

    @Override
    public IMessage onMessage(MessagePlayAnimation message, MessageContext ctx) {
        if (!PlayerAnimatorConfig.isNetworkAnimations()) {
            return null;
        }
        final MessagePlayAnimation incoming = message;
        Minecraft.getMinecraft().addScheduledTask(() -> ClientAnimationControl.play(
                incoming.getPlayerId(), incoming.getAnimationId(), incoming.getFadeIn()
        ));
        return null;
    }
}
