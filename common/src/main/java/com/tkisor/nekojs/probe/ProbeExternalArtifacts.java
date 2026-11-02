package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.authoring.AuthoringPaths;
import com.tkisor.nekojs.core.fs.NekoJSPaths;
import com.tkisor.nekojs.script.WorkspaceGenerator;

import java.nio.file.Path;

/**
 * External side-effects that {@link ProbeCoordinator} runs outside the per-backend output sync.
 * Extracted to allow isolated directory-tree testing with {@link #NONE}.
 */
public interface ProbeExternalArtifacts {

    void generate(Path outputDir) throws Exception;

    /** Keeps existing functional-interface implementations source-compatible. */
    default void generate(Path outputDir, AuthoringPaths authoringPaths) throws Exception {
        generate(outputDir);
    }

    ProbeExternalArtifacts DEFAULT = new ProbeExternalArtifacts() {
        @Override public void generate(Path outputDir) throws Exception {
            generate(outputDir, AuthoringPaths.resolve(NekoJSPaths.get().gameDir(), AuthoringPaths.DEFAULT_DIRECTORY));
        }

        @Override public void generate(Path outputDir, AuthoringPaths authoringPaths) throws Exception {
            AgentTemplateGenerator.generate(authoringPaths.verify(authoringPaths.agents()), authoringPaths.root());
            WorkspaceGenerator.createWorkspaceConfigs();
        }
    };

    ProbeExternalArtifacts NONE = outputDir -> {};
}
