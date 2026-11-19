package dev.kosmx.playerAnim.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/**
 * Server to client: "let this player's client play animation X".
 * <p>
 * Only the id travels. The client resolves it in
 * {@link dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry}, which keeps the packet
 * tiny and lets every client use its own copy of the clip.
 */
public class MessagePlayAnimation implements IMessage {

    private UUID playerId;
    private ResourceLocation animationId;
    private int fadeIn;

    public MessagePlayAnimation() {
    }

    public MessagePlayAnimation(UUID playerId, ResourceLocation animationId, int fadeIn) {
        this.playerId = playerId;
        this.animationId = animationId;
        this.fadeIn = fadeIn;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ResourceLocation getAnimationId() {
        return animationId;
    }

    public int getFadeIn() {
        return fadeIn;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.playerId = new UUID(buf.readLong(), buf.readLong());
        String rawId = ByteBufUtils.readUTF8String(buf);
        this.animationId = parseId(rawId);
        this.fadeIn = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        UUID id = this.playerId == null ? new UUID(0L, 0L) : this.playerId;
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        ByteBufUtils.writeUTF8String(buf, this.animationId == null ? "" : this.animationId.toString());
        buf.writeInt(this.fadeIn);
    }

    /** Malformed ids from a rogue server are dropped instead of throwing out of netty. */
    static ResourceLocation parseId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return new ResourceLocation(raw);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
