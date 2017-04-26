/*
 *    Copyright (c) 2016 Red Hat, Inc.
 *
 *    Red Hat licenses this file to you under the Apache License, version
 *    2.0 (the "License"); you may not use this file except in compliance
 *    with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *    implied.  See the License for the specific language governing
 *    permissions and limitations under the License.
 */

package io.fabric8.vertx.maven.plugin.utils;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The class that provides various dependency related utilities
 *
 * @author kameshs
 */
public class DependencyUtil {

    private static final List<String> PACKAGING = new ArrayList<String>() {{
        add("pom");
        add("jar");
        add("ejb");
        add("maven-plugin");
        add("rar");
        add("par");
        add("war");
        add("ear");
        add("bundle");
    }};

    /**
     * this method helps in resolving the {@link Artifact} as maven coordinates
     * coordinates ::= group:artifact:[packaging]:[classifier]:version.
     * following the conventions defined by <a href="https://maven.apache.org/pom.html#Maven_Coordinates">Maven Coordinates</a>
     *
     * @param artifact - the artifact which need to be represented as maven coordinate
     * @return string representing the maven coordinate
     */
    public static String asMavenCoordinates(Artifact artifact) {
        String artifactId = ArtifactUtils.key(artifact);
        Object[] arrCoords = StringUtils.split(artifactId, ":");

        if (artifact.hasClassifier()) {
            arrCoords = ArrayUtils.add(arrCoords, artifact.getClassifier());
        }

        if (!"jar".equals(artifact.getType())) {
            ArrayUtils.add(arrCoords, artifact.getType());
        }
        return StringUtils.join(arrCoords, ":");
    }

    /**
     * TODO-SK not sure i have covered all cases  - revisit the logic
     * This method applies a filter on a collection based on the maven co-ordinates, except for groupId and artifact id
     * places where any value could be used pass empty &quot;:&quot;
     *
     * @param mvnDependencies - the set of maven dependencies
     * @param excludes        - the maven coordinates,following standard
     *                        <a href="https://maven.apache.org/pom.html#Maven_Coordinates">Maven Coordinates</a> pattern
     * @return a collection of artifacts which are not part of exclusion
     */
    public static Set<Artifact> filteredDependencies(Set<Artifact> mvnDependencies, List<String> excludes) {

        return mvnDependencies.stream()
            .filter(artifact -> {
                String mvnCord = asMavenCoordinates(artifact);
                String[] dependencyMvnCoord = StringUtils.split(mvnCord, ":");
                ListIterator<String> exIterator = excludes.listIterator();
                String versionOrType = dependencyMvnCoord[2];
                boolean hasVersion = Artifact.VERSION_FILE_PATTERN.matcher(versionOrType).matches();
                boolean hasPackaging = PACKAGING.contains(versionOrType);

                while (exIterator.hasNext()) {
                    String[] exMvnCoords = StringUtils.split(exIterator.next(), ":");
                    int len = exMvnCoords.length;
                    switch (len) {
                        case 1: {
                            return exMvnCoords[0].equals(dependencyMvnCoord[0]);//groupId
                        }
                        case 2: {
                            return exMvnCoords[0].equals(dependencyMvnCoord[0])//groupId
                                && exMvnCoords[1].equals(dependencyMvnCoord[1]);
                        }
                        case 3: {
                            boolean isExcluded = exMvnCoords[0].equals(dependencyMvnCoord[0])//groupId
                                && exMvnCoords[1].equals(dependencyMvnCoord[1]);//artifactId
                            if (dependencyMvnCoord.length > 3) {
                                isExcluded = isExcluded &&
                                    exMvnCoords[2].equals(dependencyMvnCoord[3]);
                            } else {
                                isExcluded = isExcluded &&
                                    exMvnCoords[2].equals(dependencyMvnCoord[2]);
                            }
                            return isExcluded;
                        }
                        case 4: {
                            boolean isExcluded = exMvnCoords[0].equals(dependencyMvnCoord[0]) //groupId
                                && exMvnCoords[1].equals(dependencyMvnCoord[1]); //artifactId
                            if (hasPackaging) {
                                isExcluded = isExcluded
                                    && exMvnCoords[2].equals(dependencyMvnCoord[2]) //packaging type
                                    && exMvnCoords[3].equals(dependencyMvnCoord[3]); //classifier
                            } else {
                                // this cases where it could be default packaging as jar so we skip packaging check
                                isExcluded = isExcluded && exMvnCoords[2].equals(dependencyMvnCoord[3]); //classifier
                            }
                            return isExcluded;
                        }
                        case 5: {

                            return exMvnCoords[0].equals(dependencyMvnCoord[0]) //groupId
                                && exMvnCoords[1].equals(dependencyMvnCoord[1]) //artifactId
                                && exMvnCoords[2].equals(dependencyMvnCoord[2]) //packaging type
                                && exMvnCoords[3].equals(dependencyMvnCoord[3]) //classifier
                                && exMvnCoords[4].equals(dependencyMvnCoord[4]); //version
                        }
                    }
                }
                return true;
            }).collect(Collectors.toSet());
    }
}
