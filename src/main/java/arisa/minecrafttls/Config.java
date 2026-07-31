package arisa.minecrafttls;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALWAYS_DISABLE_VANILLA_ENCRYPTION = BUILDER
            .comment("Set to true if using an Nginx SSL reverse proxy that terminates TLS before reaching the server.")
            .define("alwaysDisableVanillaEncryption", false);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
