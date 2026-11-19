package dev.kosmx.playerAnim.network;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Server side counterpart of {@link MessageAnimationFinished}. The active-animation table is a
 * concurrent map, so the netty thread can clear it directly.
 */
public class FinishedAnimationHandler implements IMessageHandler<MessageAnimationFinished, IMessage> {

    @Override
    public IMessage onMessage(MessageAnimationFinished message, MessageContext ctx) {
        PlayerAnimatorNetwork.clearSource(message.getSourceId());
        return null;
    }
}
