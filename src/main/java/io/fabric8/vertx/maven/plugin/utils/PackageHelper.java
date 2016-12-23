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

package io.fabric8.vertx.maven.plugin.utils;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.importer.ZipImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.tmatesoft.svn.core.SVNException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackageHelper {

    private final JavaArchive archive;
    private final MavenProject mavenProject;
    private final Attributes.Name MAIN_VERTICLE = new Attributes.Name("Main-Verticle");
    private String mainVerticle;
    private String mainClass;
    private Set<Optional<File>> compileAndRuntimeDeps;
    private Set<Optional<File>> transitiveDeps;
    private Log log;
    private String outputFileName;


    public PackageHelper(MavenProject mavenProject, String mainClass, String mainVerticle) {
        this.archive = ShrinkWrap.create(JavaArchive.class);
        this.mainClass = mainClass;
        this.mainVerticle = mainVerticle;
        this.mavenProject = mavenProject;
    }


    public PackageHelper mainClass(String mainClass) {
        this.mainClass = mainClass;
        return this;
    }

    public PackageHelper mainVerticle(String mainVerticle) {
        this.mainVerticle = mainVerticle;
        return this;
    }

    /**
     * @param dir
     * @param primaryArtifactFile
     * @return
     * @throws IOException
     */
    public File build(Path dir, File primaryArtifactFile) throws MojoExecutionException {
        File classes = new File(dir.toFile(), "classes");
        build(classes, primaryArtifactFile);
        return createFatJar(dir);
    }

    /**
     * @param primaryArtifactFile
     */
    private synchronized void build(File classes, File primaryArtifactFile) throws MojoExecutionException {
        if (primaryArtifactFile != null && primaryArtifactFile.isFile()) {
            this.archive.as(ZipImporter.class).importFrom(primaryArtifactFile);
        } else if (classes.isDirectory()) {
            this.archive.addAsResource(classes, "/");
        } else {
            throw new RuntimeException("Cannot build the fat jar for the Vert.x application, neither jar file nor " +
                "classes are present");
        }

        addDependencies();
        try {
            generateManifest();
        } catch (IOException e) {
            throw new MojoExecutionException("Error building package", e);
        } catch (GitAPIException e) {
            throw new MojoExecutionException("Error building package", e);
        } catch (SVNException e) {
            throw new MojoExecutionException("Error building package", e);
        }
    }

    /**
     *
     */
    protected void addDependencies() {
        Set<Optional<File>> all = new LinkedHashSet<>(compileAndRuntimeDeps);
        all.addAll(transitiveDeps);

        all.stream()
            .filter(Optional::isPresent)
            .forEach(dep -> {
                File f = dep.get();
                if (log.isDebugEnabled()) {
                    log.debug("Adding Dependency :" + f.toString());
                }
                this.archive.as(ZipImporter.class).importFrom(f);
            });
    }

    /**
     *
     */
    protected void generateManifest() throws IOException, GitAPIException, SVNException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.MAIN_CLASS, mainClass);
        //This is a typical situation when application is launched with custom launcher
        if (mainVerticle != null) {
            attributes.put(MAIN_VERTICLE, mainVerticle);
        }

        ManifestUtils.addExtraManifestInfo(mavenProject, attributes);

        try {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            manifest.write(bout);
            bout.close();
            byte[] bytes = bout.toByteArray();
            //TODO: merge existing manifest with current one
            this.archive.setManifest(new ByteArrayAsset(bytes));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * @param dir
     * @return
     */
    private synchronized File createFatJar(Path dir) {

        File jarFile = null;

        try {
            jarFile = new File(dir.toFile(), outputFileName);
            boolean useTmpFile = false;
            File theCreatedFile = jarFile;
            if (jarFile.isFile()) {
                useTmpFile = true;
                theCreatedFile = new File(dir.toFile(), outputFileName + "__");
            }

            if (!jarFile.getParentFile().exists() && !jarFile.getParentFile().mkdirs()) {
                log.error("Failed to create parent directories for :" + jarFile.getAbsolutePath());
            }

            ZipExporter zipExporter = this.archive.as(ZipExporter.class);

            try (FileOutputStream jarOut = new FileOutputStream(theCreatedFile)) {
                zipExporter.exportTo(jarOut);
            }

            if (useTmpFile) {
                jarFile.delete();
                theCreatedFile.renameTo(jarFile);
            }

        } catch (Exception e) {
            log.error("Error building fat jar ", e);
        }

        return jarFile;
    }

    /**
     * This method will perform the service provider combination by `combining` contents of same spi
     * across the dependencies
     *
     * @param project       - the Maven project (must not be {@code null}
     * @param backupDir     - the {@link File} path that can be used to perform backups
     * @param targetJarFile - the vertx fat jar file where the spi files will be updated - typically remove and add
     * @throws MojoExecutionException - any error that might occur while doing relocation
     */
    public void combineServiceProviders(
        MavenProject project,
        Path backupDir, File targetJarFile) throws MojoExecutionException {

        try {

            Path vertxJarOriginalFile = FileUtils.backup(targetJarFile, backupDir.toFile());

            JavaArchive targetJar = ShrinkWrap.createFromZipFile(JavaArchive.class, vertxJarOriginalFile.toFile());

            List<JavaArchive> archives = Stream.concat(compileAndRuntimeDeps.stream(),
                transitiveDeps.stream())
                .filter(Optional::isPresent)
                .map(f -> ShrinkWrap.createFromZipFile(JavaArchive.class, f.get()))
                .collect(Collectors.toList());

            JavaArchive serviceCombinedArchive =
                new ServiceCombinerUtil().withLog(log)
                    .withProject(project.getArtifactId(), project.getVersion())
                    .withClassesDirectory(new File(project.getBuild().getOutputDirectory()))
                    .combine(archives);

            serviceCombinedArchive.get("/META-INF/services").getChildren()
                .forEach(n -> {
                    Asset asset = n.getAsset();
                    ArchivePath archivePath = n.getPath();
                    if (log.isDebugEnabled()) {
                        try {
                            log.debug("Asset Content: " + FileUtils.read(asset.openStream()));
                            log.debug("Adding asset:" + n.getPath());
                        } catch (IOException e) {
                            // Ignore it.
                        }
                    }
                    targetJar.delete(archivePath);
                    targetJar.add(asset, archivePath);
                });

            //delete old vertx jar file
            Files.deleteIfExists(Paths.get(targetJarFile.toURI()));

            //Create new fat jar with merged SPI
            ZipExporter zipExporter = targetJar.as(ZipExporter.class);

            try (FileOutputStream jarOut = new FileOutputStream(targetJarFile)) {
                zipExporter.exportTo(jarOut);
            }

            org.apache.commons.io.FileUtils.deleteQuietly(vertxJarOriginalFile.toFile());
        } catch (Exception e) {
            throw new MojoExecutionException("Unable to combine SPI files for " + project.getArtifactId(), e);
        }
    }

    /**
     * @param compileAndRuntimeDeps
     * @return
     */

    public PackageHelper compileAndRuntimeDeps(Set<Optional<File>> compileAndRuntimeDeps) {

        this.compileAndRuntimeDeps = compileAndRuntimeDeps;

        return this;
    }

    /**
     * @param transitiveDeps
     * @return
     */

    public PackageHelper transitiveDeps(Set<Optional<File>> transitiveDeps) {

        this.transitiveDeps = transitiveDeps;

        return this;
    }

    /**
     * @param log
     * @return
     */
    public PackageHelper log(Log log) {
        this.log = log;
        return this;
    }

    public PackageHelper withOutputName(String output) {
        this.outputFileName = output;
        return this;
    }
}
