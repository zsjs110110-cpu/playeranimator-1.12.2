package dev.kosmx.playerAnim.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/**
 * Client to server: the network animation on this player has finished playing, so the
 * server can drop its tracked state and a late observer will not be sent a finished clip again.
 * Only sent for one-shot clips; looping animations stay active and never report finished.
 */
public class MessageAnimationFinished implements IMessage {

    private UUID sourceId;

    public MessageAnimationFinished() {
    }

    public MessageAnimationFinished(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.sourceId = new UUID(buf.readLong(), buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        UUID id = sourceId == null ? new UUID(0L, 0L) : sourceId;
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
    }
}
