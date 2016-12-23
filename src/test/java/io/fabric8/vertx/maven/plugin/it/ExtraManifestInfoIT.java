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

package io.fabric8.vertx.maven.plugin.it;

import io.fabric8.vertx.maven.plugin.model.ExtraManifestKeys;
import io.fabric8.vertx.maven.plugin.utils.GitUtil;
import io.fabric8.vertx.maven.plugin.utils.ManifestUtils;
import io.fabric8.vertx.maven.plugin.utils.SVNUtil;
import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;

import java.io.File;
import java.io.IOException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kameshs
 */
public class ExtraManifestInfoIT extends VertxMojoTestBase {

    String GIT_PROJECT_ROOT = "projects/manifest-git-it";
    String SVN_PROJECT_ROOT = "projects/manifest-svn-it";

    private Verifier verifier;


    public void initVerifier(File root) throws VerificationException {
        verifier = new Verifier(root.getAbsolutePath());
        verifier.setAutoclean(false);

        installPluginToLocalRepository(verifier.getLocalRepository());
    }

    private Git prepareGitSCM(File testDir, Verifier verifier) throws IOException, GitAPIException {
        Git git = Git.init().setDirectory(testDir).call();
        File gitFolder = GitUtil.findGitFolder(testDir);
        assertThat(gitFolder).isNotNull();
        return git;
    }

    private SVNURL prepareSVNRepo(File testDir, Verifier verifier) throws SVNException {
        File svnPath = new File(testDir.getParent(), "svn-it-repo");
        //force recreation local repo for test runs
        SVNURL svnurl = SVNRepositoryFactory.createLocalRepository(svnPath, true, true);

        //Import test project into svn
        SVNClientManager svnClientManager = SVNClientManager.newInstance();

        //add some dummy files
        SVNCommitClient svnCommitter = svnClientManager.getCommitClient();

        SVNCommitInfo svnCommitInfo = svnCommitter.doImport(testDir, svnurl,
            "First Import",
            null,
            true,
            true,
            SVNDepth.INFINITY);

        System.out.println("Successfully Imported project and revisioned as " + svnCommitInfo.getNewRevision());

        return svnurl;
    }

    @Test
    public void testGITSCM() throws IOException, VerificationException, GitAPIException {
        File testDir = initProject(GIT_PROJECT_ROOT);
        assertThat(testDir).isDirectory();

        initVerifier(testDir);

        prepareProject(testDir, verifier);

        File gitFolder = GitUtil.findGitFolder(testDir);

        assertThat(testDir).isNotNull();
        assertThat(testDir.getName()).endsWith("manifest-git-it");
        assertThat(gitFolder).isNull();

        Git git = prepareGitSCM(testDir, verifier);
        gitFolder = git.getRepository().getDirectory();
        assertThat(gitFolder.getParentFile().getName()).isEqualTo(testDir.getName());
        assertThat(git.status().call().getUntracked()).contains("pom.xml", "src/main/java/demo/SimpleVerticle.java");

        //Now add and commit the file
        DirCache index = git.add().addFilepattern(".").call();
        assertThat(index.getEntryCount()).isEqualTo(2);

        git.commit().setMessage("First Import").call();

        runPackage(verifier);
        assertManifest(testDir, "git");

    }

    @Test
    public void testSVNSCM() throws SVNException, IOException, VerificationException {
        File testDir = initProject(SVN_PROJECT_ROOT);
        assertThat(testDir).isDirectory();

        initVerifier(testDir);

        prepareProject(testDir, verifier);

        File svnFolder = SVNUtil.findSvnFolder(testDir);

        assertThat(testDir).isNotNull();
        assertThat(testDir.getName()).endsWith("manifest-svn-it");
        assertThat(svnFolder).isNull();

        SVNURL svnurl = prepareSVNRepo(testDir, verifier);

        assertThat(testDir).isNotNull();
        assertThat(testDir.getName()).endsWith("manifest-svn-it");

        //Checkout sources from SVN Repo to test dir
        SVNClientManager svnClientManager = SVNClientManager.newInstance();
        SVNUpdateClient svnUpdateClient = svnClientManager.getUpdateClient();
        long checkout = svnUpdateClient.doCheckout(svnurl, testDir, SVNRevision.HEAD,
            SVNRevision.HEAD, SVNDepth.fromRecurse(true), false);

        svnFolder = SVNUtil.findSvnFolder(testDir);
        assertThat(svnFolder).isNotNull();
        assertThat(checkout).isEqualTo(1);

        runPackage(verifier);
        assertManifest(testDir, "svn");

    }

    private void assertManifest(File testDir, String scm) throws IOException {
        verifier.assertFilePresent("target/vertx-demo-start-0.0.1.BUILD-SNAPSHOT.jar");
        File jarFile = new File(testDir, "target/vertx-demo-start-0.0.1.BUILD-SNAPSHOT.jar");
        assertThat(jarFile).isNotNull();

        Manifest manifest = new JarFile(jarFile).getManifest();
        //Extract and Check Manifest for details
        assertThat(manifest).isNotNull();

        //manifest.write(System.out);

        //Check some manifest attributes
        String projectName = manifest.getMainAttributes().getValue(
            ManifestUtils.attributeName(ExtraManifestKeys.projectName.name()));
        assertThat(projectName).isEqualTo("vertx-demo-start");

        String projectGroupId = manifest.getMainAttributes().getValue(
            ManifestUtils.attributeName(ExtraManifestKeys.projectGroup.name()));
        assertThat(projectGroupId).isEqualTo("org.workspace7.maven.plugins.vertx.it");

        String projectVersion = manifest.getMainAttributes().getValue(
            ManifestUtils.attributeName(ExtraManifestKeys.projectVersion.name()));
        assertThat(projectVersion).isEqualTo("0.0.1.BUILD-SNAPSHOT");

        String projectDeps = manifest.getMainAttributes().getValue(
            ManifestUtils.attributeName(ExtraManifestKeys.projectDependencies.name()));
        assertThat(projectDeps).isNotNull();

        if ("git".equals(scm)) {

            String scmType = manifest.getMainAttributes().getValue(
                ManifestUtils.attributeName(ExtraManifestKeys.scmType.name()));
            assertThat(scmType).isNotNull();
            assertThat(scmType).isEqualTo("Git");

            String commitId = manifest.getMainAttributes().getValue(
                ManifestUtils.attributeName(ExtraManifestKeys.commitId.name()));
            assertThat(commitId).isNotNull();

            Pattern pattern = Pattern.compile("^\\w*$");
            Matcher matcher = pattern.matcher(commitId);
            assertThat(matcher.matches()).isTrue();
            assertThat(projectDeps)
                .isEqualToIgnoringWhitespace("io.vertx:vertx-core:3.3.3 io.vertx:vertx-web:3.3.3 io.vertx:vertx-jdbc-client:3.3.3");

        } else if ("svn".equals(scm)) {
            String scmType = manifest.getMainAttributes().getValue(
                ManifestUtils.attributeName(ExtraManifestKeys.scmType.name()));
            assertThat(scmType).isNotNull();
            assertThat(scmType).isEqualTo("SVN");
            String revision = manifest.getMainAttributes().getValue(
                ManifestUtils.attributeName(ExtraManifestKeys.scmRevision.name()));
            assertThat(revision).isNotNull();
            assertThat(revision).isEqualTo("1");
            assertThat(projectDeps)
                .isEqualToIgnoringWhitespace("io.vertx:vertx-core:3.3.3 io.vertx:vertx-web:3.3.3");
        }

    }


}
