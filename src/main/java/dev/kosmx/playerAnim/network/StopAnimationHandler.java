package dev.kosmx.playerAnim.network;

import dev.kosmx.playerAnim.config.PlayerAnimatorConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Client side counterpart of {@link MessagePlayAnimation}, see {@link PlayAnimationHandler}. */
public class StopAnimationHandler implements IMessageHandler<MessageStopAnimation, IMessage> {

    @Override
    public IMessage onMessage(MessageStopAnimation message, MessageContext ctx) {
        if (!PlayerAnimatorConfig.isNetworkAnimations()) {
            return null;
        }
        final MessageStopAnimation incoming = message;
        Minecraft.getMinecraft().addScheduledTask(() -> ClientAnimationControl.stop(
                incoming.getPlayerId(), incoming.getFadeOut()
        ));
        return null;
    }
}
