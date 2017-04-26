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

package io.fabric8.vertx.maven.plugin;

import io.fabric8.vertx.maven.plugin.utils.DependencyUtil;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;

/**
 * @author kameshs
 */
public class DependencyExcludeTest {

    private static final Set<Artifact> excludes = new LinkedHashSet<>();

    @BeforeClass
    public static void setupExcludes() {
        DefaultArtifactHandler defaultArtifactHandler = new DefaultArtifactHandler("jar");

        excludes.add(new DefaultArtifact("io.vertx", "web", "3.4.1",
            null, "jar", null, defaultArtifactHandler));
        excludes.add(new DefaultArtifact("io.vertx", "web", "3.4.1",
            null, "js", "client", defaultArtifactHandler));
        excludes.add(new DefaultArtifact("org.example", "acme", "1.0.1",
            null, "jar", null, defaultArtifactHandler));
        excludes.add(new DefaultArtifact("org.example", "acme2", "1.0.2",
            null, "jar", null, defaultArtifactHandler));
        excludes.add(new DefaultArtifact("com.example", "demo", "1.0.1",
            null, "jar", null, defaultArtifactHandler));
        excludes.add(new DefaultArtifact("junit", "junit", "4.2",
            null, "jar", null, defaultArtifactHandler));
        excludes.add(new DefaultArtifact("io.vertx", "vertx-core", "3.4.1",
            null, "jar", null, defaultArtifactHandler));

    }

    @Test
    public void testWithGroupIdOnly() {
        List<String> mvnCoordinates = new ArrayList<String>() {{
            add("org.example");
        }};

        Set<Artifact> matchedExcludes = DependencyUtil
            .filteredDependencies(excludes, mvnCoordinates);

        assertFalse(matchedExcludes.isEmpty());
        assertThat(matchedExcludes.size()).isEqualTo(2);

        List<String> artifactKeys = matchedExcludes.stream()
            .map(Object::toString)
            .collect(Collectors.toList());
        assertThat(artifactKeys).contains("org.example:acme:jar:1.0.1", "org.example:acme2:jar:1.0.2");
    }

    @Test
    public void testWithGroupIdArtifactId() {
        List<String> mvnCoordinates = new ArrayList<String>() {{
            add("io.vertx:web");
        }};

        Set<Artifact> matchedExcludes = DependencyUtil
            .filteredDependencies(excludes, mvnCoordinates);
        assertFalse(matchedExcludes.isEmpty());
        assertThat(matchedExcludes.size()).isEqualTo(2);
        Set<String> artifactKeys = matchedExcludes.stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
        assertThat(artifactKeys).contains("io.vertx:web:jar:3.4.1", "io.vertx:web:js:client:3.4.1");
    }

    @Test
    public void testWithGroupIdArtifactAndClassifier() {
        List<String> mvnCoordinates = new ArrayList<String>() {{
            add("io.vertx:web:client");
        }};

        Set<Artifact> matchedExcludes = DependencyUtil
            .filteredDependencies(excludes, mvnCoordinates);
        assertFalse(matchedExcludes.isEmpty());
        assertThat(matchedExcludes.size()).isEqualTo(1);
        List<String> artifactKeys = matchedExcludes.stream()
            .map(Object::toString)
            .collect(Collectors.toList());
        assertThat(artifactKeys).contains("io.vertx:web:js:client:3.4.1");
    }


}
