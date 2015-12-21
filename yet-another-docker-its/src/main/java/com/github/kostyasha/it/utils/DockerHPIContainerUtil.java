package com.github.kostyasha.it.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResult;
import ru.qatools.clay.aether.AetherException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.qatools.clay.aether.Aether.aether;
import static ru.qatools.clay.maven.settings.FluentSettingsBuilder.loadSettings;
import static ru.qatools.clay.maven.settings.MavenDefaults.getDefaultLocalRepository;

/**
 * @author Kanstantsin Shautsou
 */
public class DockerHPIContainerUtil {
    private DockerHPIContainerUtil() {
    }

    /**
     * Unused and replaced with resources dir.
     */
    public static Set<Artifact> resolvePluginsFor(String plugin) throws FileNotFoundException, SettingsBuildingException, AetherException {
        Settings settings = loadSettings()
//                .withActiveProfile("jenkins")
                .build();

        final File localRepo = new File(getDefaultLocalRepository());

        List<ArtifactResult> results = aether(localRepo, settings)
                .resolve(plugin, true)
                .get();

        final HashSet<Artifact> plugins = new HashSet<>();

        for (ArtifactResult ar : results) {
            final String candidateForHpi = ar.getArtifact().toString().replace("jar", "hpi");
            try {
                final List<ArtifactResult> candidateResults = aether(localRepo, settings)
                        .resolve(candidateForHpi, false)
                        .get();
                for (ArtifactResult candidate : candidateResults) {
                    if (candidate.getArtifact().getExtension().equals("hpi")) {
                        plugins.add(candidate.getArtifact());
                    }
                }
            } catch (AetherException ex) {
                // ignore
            }
        }

        return plugins;
    }

    public static void copyResourceFromClass(Class clazz, String file, File toFile) throws IOException {
        try (InputStream ins = clazz.getResourceAsStream(
                clazz.getSimpleName() + "/" + file
        )) {
            if (ins == null) {
                throw new IllegalStateException("Resource file " + file + " not found");
            }
            FileUtils.copyInputStreamToFile(ins, toFile);
        }
    }
    public static String getResource(Class clazz, String file) throws IOException {
        try (InputStream ins = clazz.getResourceAsStream(
                clazz.getSimpleName() + "/" + file
        )) {
            if (ins == null) {
                throw new IllegalStateException("Resource file " + file + " not found");
            }
            return IOUtils.toString(ins);
        }
    }
}
