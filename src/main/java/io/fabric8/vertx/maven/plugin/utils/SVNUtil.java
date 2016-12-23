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

import org.apache.maven.project.MavenProject;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.io.IOException;

/**
 * An utility class that is used to manipulate and extract info SVN Repository of the project
 *
 * @author kameshs
 */
public class SVNUtil {

    /**
     * this method returns the {@link SVNWCClient} from the project, this {@link SVNWCClient} will be used
     * by the Vert.x {@link io.fabric8.vertx.maven.plugin.mojos.PackageMojo} to add SVN SCM related metadata
     *
     * @param project - the maven project which is a SVN Repository
     * @return SVNWCClient
     * @throws IOException - any error occurs while getting the client
     */
    public static SVNWCClient svnWorkingCopyClient(MavenProject project) throws IOException {

        MavenProject rootProject = getRootProject(project);

        File baseDir = rootProject.getBasedir();

        if (baseDir == null) {
            baseDir = project.getBasedir();
        }

        File svnFolder = findSvnFolder(baseDir);

        if (svnFolder != null && svnFolder.exists()) {
            SVNClientManager svnClientManager = SVNClientManager.newInstance();
            SVNWCClient wcClient = svnClientManager.getWCClient();
            return wcClient;
        }
        return null;
    }

    /**
     * Method used to get the Revision of the current working copy
     *
     * @param svnwcClient - the {@link SVNWCClient} retrieved using svnWorkingCopyClient
     * @param projectDir  - the project base directory
     * @param committed   - flag used to determine which revision to retrieve from {@link SVNInfo}
     * @return current working copy SVN Revision
     * @throws SVNException - any exception that might occur while retrieving the version
     */
    public static String getRevision(SVNWCClient svnwcClient, File projectDir, boolean committed) throws SVNException {
        SVNInfo svnInfo = svnwcClient.doInfo(projectDir, SVNRevision.HEAD);

        if (committed) {
            return String.valueOf(svnInfo.getCommittedRevision());
        } else {
            return String.valueOf(svnInfo.getRevision());
        }
    }

    /**
     * Method to find the .svn folder inside the project
     *
     * @param basedir - the project base directory
     * @return - the {@link File} handle to the .svn directory
     */
    public static File findSvnFolder(File basedir) {
        File gitDir = new File(basedir, ".svn");
        if (gitDir.exists() && gitDir.isDirectory()) {
            return gitDir;
        } else {
            File parent = basedir.getParentFile();
            return parent != null ? findSvnFolder(parent) : null;
        }
    }

    /**
     * utility to find the root project
     *
     * @param project - the project whose root {@link MavenProject} need to be determined
     * @return root MavenProject of the project
     */
    public static MavenProject getRootProject(MavenProject project) {
        while (project != null) {
            MavenProject parent = project.getParent();
            if (parent == null) {
                break;
            }
            project = parent;
        }
        return project;
    }
}
