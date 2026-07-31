package arisa.minecrafttls;

import net.minecraft.client.multiplayer.ServerData;

public class mTLSContext {
    public static final ThreadLocal<ServerData> CONNECTING_SERVER = new ThreadLocal<>();
}
