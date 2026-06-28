package neelesh.easy_install.mixin;


import neelesh.easy_install.ProjectType;
import neelesh.easy_install.gui.screen.ProjectBrowser;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public class PauseScreenMixin extends Screen {
    boolean isModsButton = false;
    protected PauseScreenMixin(Component title) {
        super(title);
    }


  @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        Button quitButton = null;

        for (var widget : this.children()) {
            if (widget instanceof Button button && button.getMessage().getString().equals("Mods")) {
                isModsButton = true;
                quitButton = button;
                break;
            }
            else if (widget instanceof Button button && button.getMessage().getString().equals("Save and Quit to Title")) {
                quitButton = button;
                break;
            }
        }

        // 1. We create a clean Local Class that formally extends Button
        class UpdaterButton extends Button {
            private final Identifier icon = Identifier.tryBuild("easy_install", "widget/get_button");

			public UpdaterButton(int x, int y, int width, int height, Component message, OnPress onPress) {
				// Because we extend Button, we safely have access to the protected DEFAULT_NARRATION
				super(x,y, width, height, message, onPress, DEFAULT_NARRATION);
			}
            @Override
            protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                Identifier currentIcon = icon;
                
                // 3. Draw our custom icon perfectly centered inside the vanilla button background
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, currentIcon, this.getX(), this.getY(), this.getWidth(), this.getHeight(), -1);
            }
        }

        // Initialize our new local class
        Button updaterButton = new UpdaterButton(quitButton.getX() + quitButton.getWidth() + 20 + 4,
                quitButton.getY(), 20, 20, Component.literal("Get Mods"),
                button -> {
                    ProjectBrowser modBrowser = new ProjectBrowser(this, ProjectType.MOD);
                    Minecraft.getInstance().setScreen((Screen) ((Object) modBrowser));
                });

        this.addRenderableWidget(updaterButton);
        
        updaterButton.setTooltip(Tooltip.create(Component.literal("Get Mods")));
        
        this.addRenderableWidget(updaterButton);
    }
}
