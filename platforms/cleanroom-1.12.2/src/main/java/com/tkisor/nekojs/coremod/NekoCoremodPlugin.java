package com.tkisor.nekojs.coremod;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * cleanroom 1.12 coremod 入口。被 cleanroom loader 通过 {@code fml.coreMods.load}（dev）/
 * {@code FMLCorePlugin} manifest（生产）发现，注册 {@link NekoClassTransformer} 到 launchwrapper，
 * 后者在类加载时把 {@code @StaticInjector} 方法注入目标 MC 类。
 *
 * <p>{@link IFMLLoadingPlugin} 的其余方法都是 default（返回 null），无需覆盖。
 */
@IFMLLoadingPlugin.Name("nekojs")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.SortingIndex(1001)
public class NekoCoremodPlugin implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { NekoClassTransformer.class.getName() };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
