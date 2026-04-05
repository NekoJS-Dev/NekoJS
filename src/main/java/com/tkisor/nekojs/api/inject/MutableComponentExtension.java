package com.tkisor.nekojs.api.inject;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * @author ZZZank
 */
@SuppressWarnings("unused")
public interface MutableComponentExtension extends Component {

    // --- to be implemented ---

    default MutableComponent setStyle(Style format) {
        throw new AssertionError("no mixin impl");
    }

    default MutableComponent withStyle(ChatFormatting format) {
        throw new AssertionError("no mixin impl");
    }

    // --- extension ---

    default boolean neko$hasStyle() {
        return !getStyle().isEmpty();
    }

    default MutableComponent neko$black() {
        return withStyle(ChatFormatting.BLACK);
    }

    default MutableComponent neko$darkBlue() {
        return withStyle(ChatFormatting.DARK_BLUE);
    }

    default MutableComponent neko$darkGreen() {
        return withStyle(ChatFormatting.DARK_GREEN);
    }

    default MutableComponent neko$darkAqua() {
        return withStyle(ChatFormatting.DARK_AQUA);
    }

    default MutableComponent neko$darkRed() {
        return withStyle(ChatFormatting.DARK_RED);
    }

    default MutableComponent neko$darkPurple() {
        return withStyle(ChatFormatting.DARK_PURPLE);
    }

    default MutableComponent neko$gold() {
        return withStyle(ChatFormatting.GOLD);
    }

    default MutableComponent neko$gray() {
        return withStyle(ChatFormatting.GRAY);
    }

    default MutableComponent neko$darkGray() {
        return withStyle(ChatFormatting.DARK_GRAY);
    }

    default MutableComponent neko$blue() {
        return withStyle(ChatFormatting.BLUE);
    }

    default MutableComponent neko$green() {
        return withStyle(ChatFormatting.GREEN);
    }

    default MutableComponent neko$aqua() {
        return withStyle(ChatFormatting.AQUA);
    }

    default MutableComponent neko$red() {
        return withStyle(ChatFormatting.RED);
    }

    default MutableComponent neko$lightPurple() {
        return withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    default MutableComponent neko$yellow() {
        return withStyle(ChatFormatting.YELLOW);
    }

    default MutableComponent neko$white() {
        return withStyle(ChatFormatting.WHITE);
    }

    default MutableComponent neko$color(TextColor c) {
        return setStyle(getStyle().withColor(c));
    }

    default MutableComponent neko$noColor() {
        return neko$color(null);
    }

    default MutableComponent neko$bold(@Nullable Boolean value) {
        return setStyle(getStyle().withBold(value));
    }

    default MutableComponent neko$bold() {
        return neko$bold(true);
    }

    default MutableComponent neko$italic(@Nullable Boolean value) {
        return setStyle(getStyle().withItalic(value));
    }

    default MutableComponent neko$italic() {
        return neko$italic(true);
    }

    default MutableComponent neko$underlined(@Nullable Boolean value) {
        return setStyle(getStyle().withUnderlined(value));
    }

    default MutableComponent neko$underlined() {
        return neko$underlined(true);
    }

    default MutableComponent neko$strikethrough(@Nullable Boolean value) {
        return setStyle(getStyle().withStrikethrough(value));
    }

    default MutableComponent neko$strikethrough() {
        return neko$strikethrough(true);
    }

    default MutableComponent neko$obfuscated(@Nullable Boolean value) {
        return setStyle(getStyle().withObfuscated(value));
    }

    default MutableComponent neko$obfuscated() {
        return neko$obfuscated(true);
    }

    default MutableComponent neko$insertion(@Nullable String s) {
        return setStyle(getStyle().withInsertion(s));
    }

    default MutableComponent neko$font(@Nullable FontDescription s) {
        return setStyle(getStyle().withFont(s));
    }

    default MutableComponent neko$click(@Nullable ClickEvent s) {
        return setStyle(getStyle().withClickEvent(s));
    }

    default MutableComponent neko$clickSuggestCommand(String command) {
        return neko$click(new ClickEvent.SuggestCommand(command));
    }

    default MutableComponent neko$clickCopy(String text) {
        return neko$click(new ClickEvent.CopyToClipboard(text));
    }

    default MutableComponent neko$clickChangePage(int page) {
        return neko$click(new ClickEvent.ChangePage(page));
    }

    default MutableComponent neko$clickOpenUrl(String url) {
        return neko$click(new ClickEvent.OpenUrl(URI.create(url)));
    }

    default MutableComponent neko$clickOpenFile(String path) {
        return neko$click(new ClickEvent.OpenFile(path));
    }

    default MutableComponent neko$hover(@Nullable HoverEvent hover) {
        return setStyle(getStyle().withHoverEvent(hover));
    }

    default MutableComponent neko$hoverText(Component text) {
        return neko$hover(new HoverEvent.ShowText(text));
    }

    default MutableComponent neko$hoverItem(ItemStack stack) {
        return neko$hover(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack)));
    }

    default MutableComponent neko$hoverEntity(Entity entity) {
        return neko$hover(new HoverEvent.ShowEntity(new HoverEvent.EntityTooltipInfo(entity.getType(), entity.getUUID(), entity.getName())));
    }
}
