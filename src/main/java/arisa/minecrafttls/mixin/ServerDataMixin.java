package arisa.minecrafttls.mixin;

import arisa.minecrafttls.ImTLSServerData;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerData.class)
public class ServerDataMixin implements ImTLSServerData {
    @Unique
    private boolean minecraftTLS$mtlsEnabled = false;
    @Unique
    private String minecraftTLS$mtlsCaPath = "config/mtls/ca.crt";
    @Unique
    private String minecraftTLS$mtlsCertPath = "config/mtls/client.crt";
    @Unique
    private String minecraftTLS$mtlsKeyPath = "config/mtls/client.key";

    @Inject(method = "read", at = @At("RETURN"))
    private static void onReadNbt(CompoundTag tag, CallbackInfoReturnable<ServerData> cir) {
        ServerData serverData = cir.getReturnValue();
        if (serverData != null) {
            ImTLSServerData extendedData = (ImTLSServerData) serverData;
            extendedData.mtls$setEnabled(tag.getBoolean("mtls_enabled"));
            if (tag.contains("mtls_ca_path")) extendedData.mtls$setCaPath(tag.getString("mtls_ca_path"));
            if (tag.contains("mtls_cert_path")) extendedData.mtls$setCertPath(tag.getString("mtls_cert_path"));
            if (tag.contains("mtls_key_path")) extendedData.mtls$setKeyPath(tag.getString("mtls_key_path"));
        }
    }

    @Override
    public boolean mtls$isEnabled() {
        return minecraftTLS$mtlsEnabled;
    }

    @Override
    public void mtls$setEnabled(boolean enabled) {
        this.minecraftTLS$mtlsEnabled = enabled;
    }

    @Override
    public String mtls$getCaPath() {
        return minecraftTLS$mtlsCaPath;
    }

    @Override
    public void mtls$setCaPath(String path) {
        this.minecraftTLS$mtlsCaPath = path;
    }

    @Override
    public String mtls$getCertPath() {
        return minecraftTLS$mtlsCertPath;
    }

    @Override
    public void mtls$setCertPath(String path) {
        this.minecraftTLS$mtlsCertPath = path;
    }

    @Override
    public String mtls$getKeyPath() {
        return minecraftTLS$mtlsKeyPath;
    }

    @Override
    public void mtls$setKeyPath(String path) {
        this.minecraftTLS$mtlsKeyPath = path;
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void onWriteNbt(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        if (tag != null) {
            tag.putBoolean("mtls_enabled", this.minecraftTLS$mtlsEnabled);
            tag.putString("mtls_ca_path", this.minecraftTLS$mtlsCaPath);
            tag.putString("mtls_cert_path", this.minecraftTLS$mtlsCertPath);
            tag.putString("mtls_key_path", this.minecraftTLS$mtlsKeyPath);
        }
    }

    @Inject(method = "copyFrom", at = @At("HEAD"))
    private void onCopyFrom(ServerData other, CallbackInfo ci) {
        ImTLSServerData extendedOther = (ImTLSServerData) other;
        this.minecraftTLS$mtlsEnabled = extendedOther.mtls$isEnabled();
        this.minecraftTLS$mtlsCaPath = extendedOther.mtls$getCaPath();
        this.minecraftTLS$mtlsCertPath = extendedOther.mtls$getCertPath();
        this.minecraftTLS$mtlsKeyPath = extendedOther.mtls$getKeyPath();
    }
}
