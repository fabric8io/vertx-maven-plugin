/*
 * Copyright (c) 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.fabric8.vertx.maven.plugin.utils;

import com.google.common.base.CaseFormat;
import io.fabric8.vertx.maven.plugin.model.ExtraManifestKeys;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.jar.Attributes;
import java.util.stream.Collectors;

/**
 * The utility class that takes care of adding information to MANIFEST.MF, this information are usually
 * additional metadata about that application that can be used by tools and IDE
 *
 * @author kameshs
 */
public class ManifestUtils {

    private final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd-MMM-yyyy");

    /**
     * This method adds the extra MANIFEST.MF headers with header keys in {@link ExtraManifestKeys}
     *
     * @param project    - the maven project which is packaged as jar
     * @param attributes - the MANIFEST.MF {@link Attributes}
     * @throws IOException     - any error that might occur adding headers
     * @throws GitAPIException - any error that might occur while reading and adding Git metadata
     */
    public static void addExtraManifestInfo(MavenProject project, Attributes attributes)
        throws IOException, GitAPIException {

        Model model = project.getModel();

        attributes.put(attributeName(ExtraManifestKeys.projectName.name()),
            model.getName() == null ? model.getArtifactId() : model.getName());
        attributes.put(attributeName(ExtraManifestKeys.projectGroup.name()), model.getGroupId());
        attributes.put(attributeName(ExtraManifestKeys.projectVersion.name()), model.getVersion());

        if (project.getScm() != null) {
            Scm scm = project.getScm();
            attributes.put(attributeName(ExtraManifestKeys.scmUrl.name()), scm.getUrl());
            if (scm.getTag() != null) {
                attributes.put(attributeName(ExtraManifestKeys.scmTag.name()), scm.getTag());
            }
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
                .collect(Collectors.joining(" "));
            attributes.put(attributeName(ExtraManifestKeys.projectDependencies.name()), deps);
        }

        //SCM metadata
        File baseDir = project.getBasedir();
        if (baseDir != null) {
            File gitFolder = GitUtil.findGitFolder(baseDir);
            if (gitFolder != null) {
                Repository gitRepo = GitUtil.getGitRepository(project);
                String commitId = GitUtil.getGitCommitId(gitRepo);
                attributes.put(attributeName(ExtraManifestKeys.commitId.name()), commitId);
            }
            //TODO handle SVN
        }

    }

    /**
     * utility method to return {@link Dependency} as G:V:A:C maven coordinates
     *
     * @param dependency - the maven {@link Dependency} whose coordinates need to be computed
     * @return - the {@link Dependency} info as G:V:A:C string
     */
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

    /**
     * Simple method to return the current date/time
     *
     * @return - the current date formatted with DATE_FORMAT
     */
    public static String timestamp() {
        Date date = new Date();
        return DATE_FORMAT.format(date);
    }

    /**
     * The method will convert the camelCase names as MANIFEST.MF {@link Attributes.Name}
     * e.g.
     * if the camelCasedName is &quot;projectName&quot; - then this method will return &quot;Project-Name&quot;
     *
     * @param camelCasedName - the camel cased name that needs to be converted
     * @return converted {@link Attributes.Name} name
     */
    public static Attributes.Name attributeName(String camelCasedName) {
        return new Attributes.Name(WordUtils.capitalize(CaseFormat.LOWER_CAMEL
            .to(CaseFormat.LOWER_HYPHEN, camelCasedName), '-'));
    }
}
