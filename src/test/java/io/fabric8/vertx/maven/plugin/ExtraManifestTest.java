package io.fabric8.vertx.maven.plugin;

import io.fabric8.vertx.maven.plugin.utils.ManifestUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author kameshs
 */
public class ExtraManifestTest extends AbstractTestCase {

    @Test
    public void testExtraManifestsNoClassifer() throws Exception {
        File testJarPom = Paths.get("src/test/resources/unit/jar-packaging/pom-extramf-jar.xml").toFile();
        assertNotNull(testJarPom);
        assertTrue(testJarPom.exists());
        assertTrue(testJarPom.isFile());
        MavenProject mavenProject = new MavenProject(buildModel(testJarPom));
        assertNotNull(mavenProject);

        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION,"1.0");

        ManifestUtils.buildExtraManifestInfo(mavenProject, Collections.emptySet(), attributes);

        assertThat(attributes.isEmpty()).isFalse();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        manifest.write(bout);
        bout.flush();
        bout.close();

        String expected = "Manifest-Version: 1.0\n" +
            "Project-Name: vertx-demo\n" +
            "User-Name: kameshs\n" +
            "Project-Dependencies: io.vertx:vertx-core:3.3.3\n" +
            "Project-Group: org.vertx.demo\n" +
            "Project-Version: 1.0.0-SNAPSHOT\n" +
            "Timestamp: 22-Dec-2016\n";

        assertThat(new String(bout.toByteArray())).isEqualToIgnoringWhitespace(expected);

    }

    @Test
    public void testExtraManifestsWithClassifier() throws Exception {
        File testJarPom = Paths.get("src/test/resources/unit/jar-packaging/pom-extramf-classifier-jar.xml").toFile();
        assertNotNull(testJarPom);
        assertTrue(testJarPom.exists());
        assertTrue(testJarPom.isFile());
        MavenProject mavenProject = new MavenProject(buildModel(testJarPom));
        assertNotNull(mavenProject);

        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION,"1.0");

        ManifestUtils.buildExtraManifestInfo(mavenProject, Collections.emptySet(), attributes);

        assertThat(attributes.isEmpty()).isFalse();

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        manifest.write(bout);
        bout.flush();
        bout.close();

        String expected = "Manifest-Version: 1.0\n" +
            "Project-Name: vertx-demo\n" +
            "User-Name: kameshs\n" +
            "Project-Dependencies: com.example:example:3.3.3:vertx\n" +
            "Project-Group: org.vertx.demo\n" +
            "Project-Version: 1.0.0-SNAPSHOT\n" +
            "Timestamp: 22-Dec-2016\n";

        assertThat(new String(bout.toByteArray())).isEqualToIgnoringWhitespace(expected);

    }
}
