package com.tkisor.nekojs.truffle;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.provider.TruffleLanguageProvider;
import com.oracle.truffle.regex.RegexLanguage;

import java.util.Collection;
import java.util.List;

/**
 * 补齐 GraalMC 重打包时丢失的 TRegex（regex 语言）provider 注册。
 *
 * <p>GraalJS 解析正则字面量时会通过 Truffle 引擎按语言 id {@code regex} 查找 TRegex
 * （见 {@code RegexCompilerInterface}），而 GraalMC 的
 * {@code META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider}
 * 只保留了 {@code JavaScriptLanguageProvider} 一行，导致引擎注册表里只有 {@code [js]}，
 * 任何脚本正则都会抛 {@code No language for id regex found}。
 *
 * <p>标准 GraalVM 中该注册来自 {@code truffle-regex.jar} 的同名服务条目；本类以
 * {@code TruffleLanguageProvider} 公共 SPI 复刻 {@code RegexLanguageProvider} 的全部行为
 * （行为经反编译比对），并由 {@code META-INF/services/...TruffleLanguageProvider} 与本模块
 * 绑定注册，ServiceLoader 会与 GraalMC 的 js provider 合并加载。引擎要求 provider 类自身
 * 携带 {@link TruffleLanguage.Registration}（GraalJS 的 provider 亦然），id/name 与
 * {@link RegexLanguage} 自身的注册保持一致。
 */
@TruffleLanguage.Registration(
        id = "regex",
        name = "REGEX",
        characterMimeTypes = "application/tregex"
)
public final class NekoRegexLanguageProvider extends TruffleLanguageProvider {

    @Override
    protected String getLanguageClassName() {
        return "com.oracle.truffle.regex.RegexLanguage";
    }

    @Override
    protected Object create() {
        return new RegexLanguage();
    }

    @Override
    protected Collection<String> getServicesClassNames() {
        return List.of();
    }

    @Override
    protected List<?> createFileTypeDetectors() {
        return List.of();
    }

    @Override
    protected List<String> getInternalResourceIds() {
        return List.of();
    }

    @Override
    protected Object createInternalResource(String id) {
        throw new IllegalArgumentException(String.format("Unsupported internal resource id %s, supported ids are", id));
    }
}
