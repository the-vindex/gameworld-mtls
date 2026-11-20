package net.gameworld.mtls;

import net.minecraft.client.multiplayer.ServerData;

/** Carries the server-list entry from ConnectScreen / the pinger to Connection.connect. */
public final class GwContext {
    public static final ThreadLocal<ServerData> CONNECTING_SERVER = new InheritableThreadLocal<>();

    private GwContext() {}
}
