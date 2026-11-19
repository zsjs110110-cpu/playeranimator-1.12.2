package dev.kosmx.playerAnim.network;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/** Server to client: "drop the network animation on this player". */
public class MessageStopAnimation implements IMessage {

    private UUID playerId;
    private int fadeOut;

    public MessageStopAnimation() {
    }

    public MessageStopAnimation(UUID playerId, int fadeOut) {
        this.playerId = playerId;
        this.fadeOut = fadeOut;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getFadeOut() {
        return fadeOut;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.playerId = new UUID(buf.readLong(), buf.readLong());
        this.fadeOut = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        UUID id = this.playerId == null ? new UUID(0L, 0L) : this.playerId;
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeInt(this.fadeOut);
    }
}
