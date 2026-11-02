plugins {
    id("java")
}

// Stonecutter omits overridden shared files from generated sources, but its active node
// reads the shared tree directly. Apply the same precedence to every source-set consumer.
sourceSets.configureEach {
    for ((sources, kind) in listOf(java to "java", resources to "resources")) {
        val localRoot = layout.projectDirectory.dir("src/$name/$kind").asFile.toPath().toAbsolutePath().normalize()
        val sharedRoots = listOf(
            rootProject.file("src/$name/$kind"),
            rootProject.file("src/fabric/$kind"),
            layout.buildDirectory.dir("generated/stonecutter/$name/$kind").get().asFile,
        ).map { it.toPath().toAbsolutePath().normalize() }

        sources.exclude { candidate ->
            !candidate.isDirectory &&
                sharedRoots.any { candidate.file.toPath().toAbsolutePath().normalize().startsWith(it) } &&
                localRoot.resolve(candidate.relativePath.pathString).toFile().isFile
        }
    }
}
