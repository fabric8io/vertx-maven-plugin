/*
 *
 *   Copyright (c) 2016 Red Hat, Inc.
 *
 *   Red Hat licenses this file to you under the Apache License, version
 *   2.0 (the "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *   implied.  See the License for the specific language governing
 *   permissions and limitations under the License.
 */

package io.fabric8.vertx.maven.plugin.mojos;

import io.fabric8.vertx.maven.plugin.utils.DependencyUtil;
import io.fabric8.vertx.maven.plugin.utils.WebJars;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.LifecycleExecutor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.manager.ScmManager;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.ArtifactRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The base Mojo class that will be extended by the other plugin goals
 */
public abstract class AbstractVertxMojo extends AbstractMojo implements Contextualizable {

    /**
     * The vert.x Core Launcher class
     */

    public static final String IO_VERTX_CORE_LAUNCHER = "io.vertx.core.Launcher";
    /**
     *
     */
    protected static final String[] WILDCARD_CONFIG_FILES = new String[]{"*.yml", "*.yaml", "*.json"};
    /**
     * vert.x configuration option
     */

    protected static final String VERTX_ARG_CONF = "-conf";
    /**
     * vert.x launcher argument
     */
    protected static final String VERTX_ARG_LAUNCHER_CLASS = "--launcher-class";
    /**
     *
     */
    protected static final String DEFAULT_CONF_DIR = "/src/main/conf";

    /**
     * vert.x java-opt argument
     */
    protected static final String VERTX_ARG_JAVA_OPT = "--java-opts";

    /**
     * vert.x redeploy argument
     */
    protected static final String VERTX_ARG_REDEPLOY = "--redeploy=";

    /**
     * vert.x redeploy scan period
     */
    protected static final String VERTX_ARG_REDEPLOY_SCAN_PERIOD = "--redeploy-scan-period=";

    /**
     * vert.x redeploy grace period
     */
    protected static final String VERTX_ARG_REDEPLOY_GRACE_PERIOD = "--redeploy-grace-period=";

    /**
     * vert.x redeploy termination period
     */
    protected static final String VERTX_ARG_REDEPLOY_TERMINATION_PERIOD = "redeploy-termination-period=";

    /**
     *
     */
    protected static final String VERTX_CONFIG_FILE_JSON = "application.json";

    /**
     * vert.x command stop
     */
    protected static final String VERTX_COMMAND_STOP = "stop";

    /**
     * vert.x command start
     */
    protected static final String VERTX_COMMAND_START = "start";

    /**
     *
     */
    protected static final String VERTX_PACKAGING = "jar";

    /**
     *
     */
    protected static final String VERTX_PID_FILE = "vertx-start-process.id";

    /**
     *
     */
    protected static final String VERTX_RUN_MODE_JAR = "jar";

    /* ==== Maven deps ==== */
    /**
     * The Maven project which will define and confiure the vertx-maven-plugin
     */
    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    /**
     * The project build output directory, defaults to ${project.build.directory} which will be target directory
     * of the project
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected String projectBuildDir;

    /**
     * The maven artifact resolution session, which will be used to resolve Maven artifacts
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    /**
     * The list of remote repositories that will be used to resolve artifacts
     */
    @Parameter(alias = "remoteRepositories", defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
    protected List<ArtifactRepository> remoteRepositories;


    /* ==== Maven Components ==== */

    /**
     * The Maven Session.
     */
    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession mavenSession;

    @Component
    protected BuildPluginManager buildPluginManager;

    @Component
    protected RepositorySystem repositorySystem;

    /**
     * The component used to execute the second Maven execution.
     */
    @Component
    protected LifecycleExecutor lifecycleExecutor;

    @Component
    protected ScmManager scmManager;

    /* ==== Config ====  */
    // TODO-ROL: It would be awesome if this would not be required but, if not given,
    // the plugin tries to detect a single verticle. Maybe even decorated with a specific annotation ?
    // (like @MainVerticle ?). Only if no such verticle can be uniquely identified, then throw an exception.
    /**
     * The verticle that will be the main entry point on to the vertx application, the same property will be used
     * as &quot;Main-Verticle;&quot; attribute value on the MANIFEST.MF
     */
    @Parameter(alias = "verticle", property = "vertx.verticle")
    protected String verticle;

    /**
     * The main launcher class that will be used when launching the Vert.X applications.
     * It defaults to {@code io.vertx.core.Launcher}
     */
    @Parameter(defaultValue = "io.vertx.core.Launcher", property = "vertx.launcher")
    protected String launcher;

    /**
     * Skip (globally) the processing made by this plugin. All mojos are impacted by this parameter.
     */
    @Parameter(property = "vertx.skip", defaultValue = "false")
    protected boolean skip;

    /**
     * The dependencies that should be excluded when performing the fat jar
     * The dependencies will be of maven g:[a::c] format.
     * <pre>
     *     <dependencyExcludes>
     *         Excludes all artifacts under groupId <b>org.acme</b>
     *         <dependencyExclude>>org.acme</dependencyExclude>
     *          Excludes all artifacts under groupId org.acme with artifactId dummy with any classifier
     *         <dependencyExclude>org.acme:dummy</dependencyExclude>
     *         Excludes all artifacts under groupId <b>org.acme</b> with artifactId dummy with <b>classifier</b> client
     *         <dependencyExclude>org.acme:dummy::client</dependencyExclude>
     *         (or)
     *         <dependencyExclude>org.acme:dummy:1.0.0:client</dependencyExclude>
     *         When using classifer it's required to provide specific like <b>1.0.0</b> or empty version <b>:</b>
     *     </dependencyExcludes>
     * </pre>
     *
     * @since 1.0.8
     */
    @Parameter(alias = "dependencyExcludes", property = "vertx.dependency.excludes")
    protected List<String> dependencyExcludes;

    /**
     * The Plexus container.
     */
    protected PlexusContainer container;

    public MavenProject getProject() {
        return project;
    }

    /**
     * this method resolves maven artifact from all configured repositories using the maven coordinates
     *
     * @param artifact - the maven coordinates of the artifact
     * @return {@link Optional} {@link File} pointing to the resolved artifact in local repository
     */
    protected Optional<File> resolveArtifact(String artifact) {
        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(new org.eclipse.aether.artifact.DefaultArtifact(artifact));
        try {
            ArtifactResult artifactResult = repositorySystem.resolveArtifact(repositorySystemSession, artifactRequest);
            if (artifactResult.isResolved()) {
                getLog().debug("Resolved :" + artifactResult.getArtifact().getArtifactId());
                return Optional.of(artifactResult.getArtifact().getFile());
            } else {
                getLog().error("Unable to resolve:" + artifact);
            }
        } catch (ArtifactResolutionException e) {
            getLog().error("Unable to resolve:" + artifact);
        }

        return Optional.empty();
    }

    /**
     * this method helps in extracting the Artifact paths from the Maven local repository.
     * If does does not handle WebJars and non-jar dependencies.
     *
     * @param artifacts - the collection of artifacts which needs to be resolved to local {@link File}
     * @return A {@link Set} of {@link Optional} file paths
     */
    protected Set<Optional<File>> extractArtifactPaths(Set<Artifact> artifacts) {
        return artifacts
            .stream()
            .filter(e -> e.getScope().equals("compile") || e.getScope().equals("runtime"))
            .filter(e -> e.getType().equalsIgnoreCase("jar"))
            .filter(e -> !WebJars.isWebJar(getLog(), e.getFile()))
            .map(DependencyUtil::asMavenCoordinates)
            .distinct()
            .map(this::resolveArtifact)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }


    /**
     * This method returns the project's primary artifact file, this method tries to compute the artifact file name
     * based on project finalName is configured or not
     *
     * @param artifact - the project artifact for which the target file will be needed
     * @return {@link Optional<File>} representing the optional project primary artifact file
     */
    protected Optional<File> getArtifactFile(Artifact artifact) {
        final String finalName = this.project.getName();
        if (artifact == null) {
            Path finalNameArtifact = Paths.get(this.projectBuildDir, finalName + "." + this.project.getPackaging());
            if (Files.exists(finalNameArtifact)) {
                return Optional.of(finalNameArtifact.toFile());
            }
        } else {
            return Optional.ofNullable(artifact.getFile());
        }
        return Optional.empty();
    }

    /**
     * Retrieves the Plexus container.
     *
     * @param context the context
     * @throws ContextException if the container cannot be retrieved.
     */
    @Override
    public void contextualize(Context context) throws ContextException {
        container = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
    }

}
