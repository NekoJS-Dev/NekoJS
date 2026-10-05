package com.tkisor.nekojs.api.ui;

/**
 * Resolves controlled UI resource ids against the ticket 29 resource roots and the
 * platform resource manager. There is exactly one resource root policy: the NekoJS
 * disk pack plus the vanilla resource stack — no second root, no second event.
 *
 * <p>Resolving never throws for a bad or missing resource; the status carries the
 * diagnostic. Icons are textures by id; item-stack icons go through the item
 * binding instead of this resolver.
 */
public interface UiResourceResolver {
    /**
     * Resolves a texture id such as {@code mymod:gui/panel} (the {@code .png}
     * suffix is implied when absent).
     *
     * @param id raw resource id from script props
     * @return resolution status, never null
     */
    ResourceStatus resolveTexture(String id);

    /**
     * Resolves a font id such as {@code mymod:custom} to its definition under
     * {@code font/<path>.json}. The optional {@code .json} suffix is accepted.
     * A resolved definition is available for lookup; it does not establish that
     * the platform has successfully decoded or loaded every font provider.
     *
     * @param id raw resource id from script props
     * @return resolution status, never null
     */
    ResourceStatus resolveFont(String id);
}
