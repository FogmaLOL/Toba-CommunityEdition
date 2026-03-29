package dev.toba.client.api.imgui;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import imgui.*;
import imgui.extension.implot.ImPlot;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import org.apache.commons.io.IOUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30C;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ImGuiImpl {
    private static final ImGuiImplGlfw imGuiImplGlfw = new ImGuiImplGlfw();
    private static final ImGuiImplGl3 imGuiImplGl3 = new ImGuiImplGl3();
    private static final Map<String, Map<String, ImFont>> FONT_MAP = new HashMap<>();

    // Global HUD overlays that render every frame (even without a Screen open)
    private static final List<RenderInterface> hudOverlays = new CopyOnWriteArrayList<>();

    public static void registerHudOverlay(RenderInterface overlay) {
        hudOverlays.add(overlay);
    }

    public static void unregisterHudOverlay(RenderInterface overlay) {
        hudOverlays.remove(overlay);
    }

    public static List<RenderInterface> getHudOverlays() {
        return hudOverlays;
    }
    public static String currentFontFamily = "Rubik";

    public static void create(final long handle) {
        ImGui.createContext();
        ImPlot.createContext();

        ImGuiIO data = ImGui.getIO();
        data.setIniFilename("toba.ini");
        data.setConfigFlags(ImGuiConfigFlags.DockingEnable);

        String[][] fontSpecs = {
                {"Rubik", "Rubik-VariableFont_wght.ttf"},
                {"Roboto", "RobotoMono-VariableFont_wght.ttf"},
                {"Bebas", "BebasNeue-Regular.ttf"},
                {"Playwrite", "PlaywriteINGuides-Regular.ttf"},
                {"OpenSans", "OpenSans-Italic-VariableFont_wdth,wght.ttf"},
                {"Minecraft", "Minecraft.ttf"}
        };

        for (String[] spec : fontSpecs) {
            String name = spec[0];
            String path = "/fonts/" + spec[1];
            try {
                Map<String, ImFont> sizes = new HashMap<>();
                sizes.put("regular", loadFont(path, 16));
                sizes.put("bold", loadFont(path, 18));
                sizes.put("title", loadFont(path, 22));
                FONT_MAP.put(name, sizes);
            } catch (Exception e) {
                System.err.println("[Toba] Failed to load font " + name + ": " + e.getMessage());
            }
        }
        try {
            data.getFonts().build();
        } catch (Exception e) {
            e.printStackTrace();
        }

        imGuiImplGlfw.init(handle, true);
        imGuiImplGl3.init();
    }

    public static ImFont getActiveFont(String type) {
        Map<String, ImFont> family = FONT_MAP.get(currentFontFamily);
        if (family == null) family = FONT_MAP.get("Rubik");
        if (family == null || family.isEmpty()) return null;
        return family.getOrDefault(type.toLowerCase(), family.get("regular"));
    }

    /**
     * Begin the main ImGui render pass — processes GLFW input and sets display size.
     * Use this for the primary ImGui frame (ClickGUI, HUD overlays) in GameRendererMixin.
     */
    public static void beginFrame() {
        final Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        int fboId = ((GlTexture) framebuffer.getColorAttachment()).getOrCreateFramebuffer(
                ((GlBackend) RenderSystem.getDevice()).getBufferManager(), null);

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fboId);
        GL11C.glViewport(0, 0, framebuffer.textureWidth, framebuffer.textureHeight);

        imGuiImplGl3.newFrame();
        imGuiImplGlfw.newFrame();
        ImGui.newFrame();

        ImFont font = getActiveFont("regular");
        if (font != null) ImGui.pushFont(font);
    }

    /**
     * Begin an ImGui render pass for drawing only — does NOT process GLFW input.
     * Use this for mini ImGui passes (e.g. inventory background/text) that must
     * not consume mouse/keyboard events needed by the main ClickGUI pass.
     */
    public static void beginImGuiRendering() {
        final Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
        int fboId = ((GlTexture) framebuffer.getColorAttachment()).getOrCreateFramebuffer(
                ((GlBackend) RenderSystem.getDevice()).getBufferManager(), null);

        GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fboId);
        GL11C.glViewport(0, 0, framebuffer.textureWidth, framebuffer.textureHeight);

        imGuiImplGl3.newFrame();
        // Skip imGuiImplGlfw.newFrame() — preserves input for the main pass
        ImGui.newFrame();

        ImFont font = getActiveFont("regular");
        if (font != null) ImGui.pushFont(font);
    }

    public static void endImGuiRendering() {
        if (getActiveFont("regular") != null) ImGui.popFont();
        ImGui.render();
        imGuiImplGl3.renderDrawData(ImGui.getDrawData());
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            long pointer = GLFW.glfwGetCurrentContext();
            ImGui.updatePlatformWindows();
            ImGui.renderPlatformWindowsDefault();
            GLFW.glfwMakeContextCurrent(pointer);
        }
    }

    private static short[] defaultRanges;
    private static short[] extendedRanges;

    private static ImFont loadFont(final String path, final int pixelSize) {
        if (defaultRanges == null) {
            ImFontGlyphRangesBuilder defBuilder = new ImFontGlyphRangesBuilder();
            defBuilder.addRanges(ImGui.getIO().getFonts().getGlyphRangesDefault());
            defaultRanges = defBuilder.buildRanges();

            ImFontGlyphRangesBuilder extBuilder = new ImFontGlyphRangesBuilder();
            extBuilder.addRanges(ImGui.getIO().getFonts().getGlyphRangesDefault());
            extBuilder.addRanges(ImGui.getIO().getFonts().getGlyphRangesCyrillic());
            extendedRanges = extBuilder.buildRanges();
        }
        // Fonts that likely lack Cyrillic glyphs get default-only ranges
        boolean useExtended = !path.contains("Minecraft") && !path.contains("Bebas") && !path.contains("Playwrite");
        ImFontConfig config = new ImFontConfig();
        config.setGlyphRanges(useExtended ? extendedRanges : defaultRanges);
        try (InputStream in = ImGuiImpl.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Font not found: " + path);
            return ImGui.getIO().getFonts().addFontFromMemoryTTF(IOUtils.toByteArray(in), pixelSize, config);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            config.destroy();
        }
    }

    public static void dispose() {
        imGuiImplGl3.shutdown();
        imGuiImplGlfw.shutdown();
        ImPlot.destroyContext();
        ImGui.destroyContext();
    }
}