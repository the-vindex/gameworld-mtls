package net.gameworld.mtls;

/** Added to net.minecraft.network.Connection by ConnectionMixin. */
public interface GwConnection {
    GwConfig.Match gw$match();

    void gw$setMatch(GwConfig.Match match);

    Enrollment.State gw$enroll();
}
