package com.tkisor.nekojs.core.plugin;

import com.tkisor.nekojs.api.NekoJSPlugin;
import com.tkisor.nekojs.api.ScriptType;
import com.tkisor.nekojs.core.NekoJSBasePluginManager;
import com.tkisor.nekojs.script.ScriptTypeEnv;
import com.tkisor.nekojs.wrapper.DataGenerationBatch;
import com.tkisor.nekojs.wrapper.DataGeneratorJS;
import com.tkisor.nekojs.wrapper.LangGeneratorJS;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The platform layer fires generateData, generateAssets and generateLang on every plugin
 * during a resource reload. These hooks are called directly: they have empty default
 * implementations, and overriding one is all a plugin does; no registration or interface
 * declaration is needed.
 *
 * <p>Plugin hooks fire before the script events and share the same generator instance with
 * them, matching KubeJS. Each plugin call is wrapped in try/catch, so one failing plugin does
 * not interrupt the others.
 */
public final class PluginGenerationHooks {
    private PluginGenerationHooks() {}

    public static void fireGenerateData(DataGeneratorJS generator) {
        fire("generateData", ScriptType.SERVER, generator, NekoJSPlugin::generateData);
    }

    /**
     * Runs the plugin {@code generateData} hook and the script
     * {@code ServerEvents.generateData} event through one data generation path: the same
     * candidate collection, generation, publish and read-back ({@link DataGenerationBatch}).
     * Plugins fire first, with per-plugin contributor tagging and exception isolation, matching
     * KubeJS; then {@code scriptEvent} runs, and the platform adapter posts the script event with
     * the stage as dispatch key; then the batch is validated and atomically published.
     *
     * <p>Failure behavior: a plugin that throws is isolated (the error log names the plugin
     * class) and does not block the batch. If {@code scriptEvent} itself throws (production
     * script listener errors are recorded by the event bus instead), or validation or publish
     * fails, nothing is published, the previously active data stays in place, and the candidate
     * area is kept for diagnostics until the next batch for the same stage opens.
     *
     * @param dataRoot   pack-visible data root (e.g. {@code <gameDir>/nekojs/data})
     * @param stage      generation stage, used as the dispatch key of the script event
     *                   (e.g. {@code after_mods})
     * @param scriptEvent posts the script event with the batch's shared generator
     */
    public static DataGenerationBatch.PublishResult runGenerateData(
            Path dataRoot, String stage, Consumer<DataGeneratorJS> scriptEvent) throws IOException {
        DataGenerationBatch batch = DataGenerationBatch.open(dataRoot, stage);
        DataGeneratorJS generator = batch.generator();
        fire("generateData", ScriptType.SERVER, generator, (plugin, gen) -> {
            batch.setContributor("plugin:" + plugin.getClass().getName());
            try {
                plugin.generateData(gen);
            } finally {
                batch.setContributor(null);
            }
        });
        batch.setContributor("script");
        try {
            scriptEvent.accept(generator);
        } finally {
            batch.setContributor(null);
        }
        return batch.publish();
    }

    public static void fireGenerateAssets(DataGeneratorJS generator) {
        fire("generateAssets", ScriptType.CLIENT, generator, NekoJSPlugin::generateAssets);
    }

    public static void fireGenerateLang(LangGeneratorJS generator) {
        fire("generateLang", ScriptType.CLIENT, generator, NekoJSPlugin::generateLang);
    }

    /**
     * Works out the languages to generate this round: the union of the languages declared by
     * plugins through {@link NekoJSPlugin#generatedLangs()} and the languages of the script
     * keyed listeners, returned in lexicographic order.
     *
     * <p>The caller passes the script languages in {@code scriptRegisteredLangs} (from
     * {@code ClientEvents.LANG
     * .registeredKeys()}). The event bus is an MC-facing type and this
     * class lives in common, which cannot reference it, so the platform adapter takes the keys
     * and this class only merges, checks and sorts them. {@code null} explicitly means "this
     * node has no script language listeners"; that is a legal state, not a missing value, and
     * it joins the union as the empty set. A {@code null} declaration on the plugin side is
     * the opposite: it is a violation (see below).
     *
     * <p>The deterministic order is part of the contract: {@code registeredKeys()} comes from
     * {@code Set.copyOf}, which does not guarantee order, while the write order per language is
     * observable (diagnostic order, and the final value when the same key is overridden), so a
     * {@link TreeSet} normalizes it.
     *
     * <p>The default {@code en_us} comes from the default implementation of
     * {@link NekoJSPlugin#generatedLangs()}, so plugin callbacks still fire when no script
     * listener exists and are not silently gated away.
     *
     * <p>The check uses {@link LangGeneratorJS#isValidLangCode(String)}, the same rule as the
     * write phase, so a language that passes this precheck is not rejected by {@code writeTo}
     * under different rules.
     *
     * <p>Validation completes <b>before</b> this method returns: any invalid language code
     * rejects the whole batch ({@link IllegalStateException}, with the owner and the invalid
     * code in the message), and the caller must not write any language file. If one plugin's
     * {@code generatedLangs()} throws, only that plugin's declaration is dropped (an error is
     * logged) and processing continues, leaving other plugins and languages untouched.
     */
    public static List<String> resolveGeneratedLangs(Collection<String> scriptRegisteredLangs) {
        TreeSet<String> langs = new TreeSet<>();
        List<String> violations = new ArrayList<>();

        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            Collection<String> declared;
            try {
                declared = plugin.generatedLangs();
            } catch (Exception e) {
                ScriptTypeEnv.logger(ScriptType.CLIENT)
                        .error("generatedLangs hook failed for " + plugin.getClass().getName()
                                + "; its language declarations are skipped", e);
                continue;
            }
            if (declared == null) {
                violations.add(plugin.getClass().getName() + ": null");
                continue;
            }
            for (String lang : declared) {
                if (!LangGeneratorJS.isValidLangCode(lang)) {
                    violations.add(plugin.getClass().getName() + ": " + lang);
                } else {
                    langs.add(lang);
                }
            }
        }

        if (scriptRegisteredLangs != null) {
            for (String lang : scriptRegisteredLangs) {
                if (!LangGeneratorJS.isValidLangCode(lang)) {
                    violations.add("script lang listener: " + lang);
                } else {
                    langs.add(lang);
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid generated language code(s); rejecting the whole language batch before any"
                            + " lang file is written: " + String.join(", ", violations));
        }
        return List.copyOf(langs);
    }

    /** Shared shape for firing on every plugin with exception isolation: the hook name goes into
     * the log, the environment picks the logger, and the generator type is a parameter. */
    private static <G> void fire(String hook, ScriptType env, G generator,
            BiConsumer<NekoJSPlugin, G> call) {
        for (NekoJSPlugin plugin : NekoJSBasePluginManager.getPlugins()) {
            try {
                call.accept(plugin, generator);
            } catch (Exception e) {
                ScriptTypeEnv.logger(env).error(hook + " hook failed for " + plugin.getClass().getName(), e);
            }
        }
    }
}
