package com.tkisor.nekojs.network;

import com.tkisor.nekojs.wrapper.clientdata.ClientDataStore;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/**
 * 1.12.2 client data sync packet: a key plus a JSON string. Server scripts push
 * values via {@link com.tkisor.nekojs.wrapper.clientdata.ClientDataSyncJS};
 * {@link ClientDataMessageHandler} writes them into the common
 * {@link ClientDataStore} on the receiving client. The JSON travels as a
 * string so the wire format matches the NeoForge 26/1.21.1 packets.
 *
 * <p>The empty constructor is required by FML for reflective instantiation
 * when deserializing incoming packets.
 */
public class ClientDataSyncPacket implements IMessage {

    private String key;
    private String json;

    /** Required for reflective deserialization. */
    public ClientDataSyncPacket() {
        this.key = "";
        this.json = "null";
    }

    public ClientDataSyncPacket(String key, String json) {
        this.key = validateKey(key);
        this.json = json != null ? json : "null";
    }

    public String key() {
        return key;
    }

    public String json() {
        return json;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.key = validateKey(ByteBufUtils.readUTF8String(buf));
        this.json = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, this.key);
        ByteBufUtils.writeUTF8String(buf, this.json);
    }

    private static String validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > ClientDataStore.MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Client data key must be non-blank and at most "
                    + ClientDataStore.MAX_KEY_LENGTH + " characters");
        }
        return key;
    }
}
