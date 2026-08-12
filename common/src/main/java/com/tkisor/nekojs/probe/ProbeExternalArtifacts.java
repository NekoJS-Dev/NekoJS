package com.tkisor.nekojs.probe;

import com.tkisor.nekojs.probe.AgentTemplateGenerator;
import com.tkisor.nekojs.script.WorkspaceGenerator;

import java.nio.file.Path;

/**
 * External side-effects that {@link ProbeCoordinator} runs outside the per-backend staging swap.
 * Extracted to allow isolated directory-tree testing with {@link #NONE}.
 */
public interface ProbeExternalArtifacts {

    void generate(Path outputDir) throws Exception;

    ProbeExternalArtifacts DEFAULT = outputDir -> {
        Path agentsDir = outputDir.getParent().resolve(".github").resolve("agents");
        AgentTemplateGenerator.generate(agentsDir);
        WorkspaceGenerator.createWorkspaceConfigs();
    };

    ProbeExternalArtifacts NONE = outputDir -> {};
}
