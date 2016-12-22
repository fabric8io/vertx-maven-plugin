package io.fabric8.vertx.maven.plugin;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.File;
import java.io.IOException;

/**
 * @author kameshs
 */
public class AbstractTestCase {

    protected Model buildModel(File pomFile) {
        try {
            return new MavenXpp3Reader().read(ReaderFactory.newXmlReader(pomFile));
        } catch (IOException var3) {
            throw new RuntimeException("Failed to read POM file: " + pomFile, var3);
        } catch (XmlPullParserException var4) {
            throw new RuntimeException("Failed to parse POM file: " + pomFile, var4);
        }
    }
}
