package arisa.minecrafttls.mixin;

import arisa.minecrafttls.mTLSConfigScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditServerScreen.class)
public abstract class ServerEditScreenMixin extends Screen {

    @Shadow
    @Final
    private ServerData serverData;

    @Shadow
    private EditBox ipEdit;

    protected ServerEditScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addMtlsButton(CallbackInfo ci) {
        this.ipEdit.setWidth(145);

        int btnX = this.ipEdit.getX() + 150;
        int btnY = this.ipEdit.getY();

        this.addRenderableWidget(Button.builder(
                Component.literal("mTLS"),
                (button) -> this.minecraft.setScreen(new mTLSConfigScreen(this, this.serverData))
        ).bounds(btnX, btnY, 50, 20).build());
    }
}