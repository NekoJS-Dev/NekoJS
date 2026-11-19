package com.tkisor.nekojs.client;

import com.mojang.blaze3d.font.GlyphInfo;
import com.tkisor.nekojs.NekoJS;
import com.tkisor.nekojs.stage.LegacyStageNotification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

public final class LegacyStageNotificationClient {
    private static final String FONT_PREFIX = "stage_notification/";
    private static final ResourceLocation ICON_FONT = ResourceLocation.fromNamespaceAndPath(NekoJS.MODID, "stage_notification");
    private static final Map<ResourceLocation, IconFont> ICON_FONTS = new WeakHashMap<>();
    private static final char ICON_CHARACTER = '\u00a0';
    private static final GlyphInfo.SpaceGlyphInfo ICON_ADVANCE = () -> 10.0F;
    private static final GlyphInfo.SpaceGlyphInfo NO_ADVANCE = () -> 0.0F;
    private static long nextFontId;

    private LegacyStageNotificationClient() {}

    public static void receive(LegacyStageNotification notification, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != context.listener()) {
            return;
        }
        ResourceLocation font = createIconFont(client, notification.icon());
        notification.title().ifPresent(title -> {
            client.gui.setTimes(10, 60, 10);
            client.gui.setTitle(withIcon(title, font));
            client.gui.setSubtitle(notification.subtitle().orElseGet(Component::empty));
        });
        notification.actionBar().ifPresent(text -> client.getChatListener().handleSystemMessage(withIcon(text, font), true));
        notification.chat().ifPresent(text -> client.getChatListener().handleSystemMessage(withIcon(text, font), false));
    }

    private static @Nullable ResourceLocation createIconFont(Minecraft client, ResourceLocation icon) {
        BakedGlyph glyph = resolveGlyph(client, icon);
        if (glyph == null) {
            return null;
        }
        ResourceLocation name = ICON_FONT.withPath(FONT_PREFIX + nextFontId++);
        ICON_FONTS.put(name, new IconFont(client, icon, glyph));
        return name;
    }

    private static Component withIcon(Component text, @Nullable ResourceLocation font) {
        return font == null ? text : Component.empty()
                .append(Component.literal(String.valueOf(ICON_CHARACTER)).withStyle(style -> style.withFont(font)))
                .append(text);
    }

    public static @Nullable FontSet findIconFont(ResourceLocation name) {
        return NekoJS.MODID.equals(name.getNamespace()) && name.getPath().startsWith(FONT_PREFIX)
                ? ICON_FONTS.get(name) : null;
    }

    static void clear() {
        ICON_FONTS.clear();
    }

    static void invalidateTextures() {
        ICON_FONTS.values().forEach(font -> font.needsReload = true);
    }

    private static @Nullable BakedGlyph resolveGlyph(Minecraft client, ResourceLocation icon) {
        String path = icon.getPath();
        if (path.startsWith("textures/") || path.endsWith(".png")) {
            ResourceLocation texture = path.startsWith("textures/") ? icon : icon.withPrefix("textures/");
            return textureGlyph(client, texture);
        }

        Item item = BuiltInRegistries.ITEM.getOptional(icon).orElse(null);
        if (item != null || path.startsWith("block/") || path.startsWith("item/")) {
            ResourceLocation spriteId = item == null ? icon : icon.withPrefix(item instanceof BlockItem ? "block/" : "item/");
            TextureAtlasSprite sprite = client.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(spriteId);
            return isPresent(sprite) ? spriteGlyph(sprite)
                    : textureGlyph(client, spriteId.withPath("textures/" + spriteId.getPath() + ".png"));
        }
        TextureAtlasSprite sprite = client.getGuiSprites().getSprite(icon);
        return isPresent(sprite) ? spriteGlyph(sprite) : null;
    }

    private static boolean isPresent(@Nullable TextureAtlasSprite sprite) {
        return sprite != null && !MissingTextureAtlasSprite.getLocation().equals(sprite.contents().name());
    }

    private static BakedGlyph spriteGlyph(TextureAtlasSprite sprite) {
        return new BakedGlyph(GlyphRenderTypes.createForColorTexture(sprite.atlasLocation()),
                sprite.getU0(), sprite.getU1(), sprite.getV0(), sprite.getV1(), 0.0F, 8.0F, 0.0F, 8.0F);
    }

    private static @Nullable BakedGlyph textureGlyph(Minecraft client, ResourceLocation texture) {
        if (!texture.getPath().endsWith(".png") || client.getResourceManager().getResource(texture).isEmpty()) {
            return null;
        }
        return new BakedGlyph(GlyphRenderTypes.createForColorTexture(texture),
                0.0F, 1.0F, 0.0F, 1.0F, 0.0F, 8.0F, 0.0F, 8.0F);
    }

    private static final class IconFont extends FontSet {
        private final Minecraft client;
        private final ResourceLocation icon;
        private @Nullable BakedGlyph glyph;
        private boolean needsReload;

        private IconFont(Minecraft client, ResourceLocation icon, BakedGlyph glyph) {
            super(client.getTextureManager(), ICON_FONT);
            this.client = client;
            this.icon = icon;
            this.glyph = glyph;
        }

        @Override
        public GlyphInfo getGlyphInfo(int codepoint, boolean filterFishyGlyphs) {
            return codepoint == ICON_CHARACTER && currentGlyph() != null ? ICON_ADVANCE : NO_ADVANCE;
        }

        @Override
        public BakedGlyph getGlyph(int codepoint) {
            BakedGlyph current = codepoint == ICON_CHARACTER ? currentGlyph() : null;
            return current == null ? EmptyGlyph.INSTANCE : current;
        }

        private @Nullable BakedGlyph currentGlyph() {
            if (needsReload) {
                glyph = resolveGlyph(client, icon);
                needsReload = false;
            }
            return glyph;
        }
    }
}
