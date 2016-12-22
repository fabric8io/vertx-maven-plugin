package io.fabric8.vertx.maven.plugin.utils;

import com.google.common.base.CaseFormat;
import io.fabric8.vertx.maven.plugin.model.ExtraManifestKeys;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.stream.Collectors;

/**
 * @author kameshs
 */
public class ManifestUtils {

    private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MMM-yyyy");

    public static void buildExtraManifestInfo(MavenProject project, Set<Artifact> depArtifacts, Attributes attributes) {

        Model model = project.getModel();

        attributes.put(attributeName(ExtraManifestKeys.projectName.name()),
            model.getName() == null ? model.getArtifactId() : model.getName());
        attributes.put(attributeName(ExtraManifestKeys.projectGroup.name()), model.getGroupId());
        attributes.put(attributeName(ExtraManifestKeys.projectVersion.name()), model.getVersion());

        if (project.getScm() != null) {
            attributes.put(attributeName(ExtraManifestKeys.scmUrl.name()), model.getScm().getUrl());
        }

        if (project.getUrl() != null) {
            attributes.put(attributeName(ExtraManifestKeys.projectUrl.name()), model.getUrl());
        }

        attributes.put(attributeName(ExtraManifestKeys.timestamp.name()), timestamp());
        attributes.put(attributeName(ExtraManifestKeys.userName.name()), System.getProperty("user.name"));

        List<Dependency> dependencies = model.getDependencies();

        if (dependencies != null && !dependencies.isEmpty()) {

            String deps = dependencies.stream()
                .filter(d -> "compile".equals(d.getScope()) || null == d.getScope())
                .map((d) -> asCoordinates(d))
                .collect(Collectors.joining(";"));
            attributes.put(attributeName(ExtraManifestKeys.projectDependencies.name()), deps);
        }
    }

    protected static String asCoordinates(Dependency dependency) {

        StringBuilder dependencyCoordinates = new StringBuilder().
            append(dependency.getGroupId())
            .append(":")
            .append(dependency.getArtifactId())
            .append(":")
            .append(dependency.getVersion());

        if (dependency.getClassifier() != null) {
            dependencyCoordinates.append(":").append(dependency.getClassifier());
        }

        return dependencyCoordinates.toString();
    }

    private static String timestamp() {
        Date date = new Date();
        return DATE_FORMAT.format(date);
    }

    private static Attributes.Name attributeName(String name) {
        return new Attributes.Name(WordUtils.capitalize(CaseFormat.LOWER_CAMEL
            .to(CaseFormat.LOWER_HYPHEN, name), '-'));
    }
}
