/*
 *
 *   Copyright (c) 2016-2017 Red Hat, Inc.
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

package io.reactiverse.vertx.maven.plugin.utils;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.maven.plugin.logging.Log;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * @author kameshs
 */
public class IncrementalBuilder extends FileAlterationListenerAdaptor implements Runnable, Closeable {

    private final Log logger;

    private final List<Callable<Void>> chain;

    private FileAlterationMonitor monitor;

    private Hashtable<Path, FileAlterationObserver> observers = new Hashtable<>();

    public IncrementalBuilder(Set<Path> inclDirs,
                              List<Callable<Void>> chain,
                              Log logger, long watchTimeInterval) {
        this.chain = chain;
        this.logger = logger;
        this.monitor = new FileAlterationMonitor(watchTimeInterval);
        inclDirs.forEach(this::buildObserver);

    }

    @Override
    public void run() {
        try {
            this.monitor.start();
        } catch (Exception e) {
            logger.error("Unable to start Incremental Builder", e);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.monitor != null) {
            try {
                this.monitor.stop();
            } catch (Exception e) {
                //ignore
            }
        }
    }

    /**
     * Adds an observer listening for changes in the given path.
     *
     * @param path the path to observe
     */
    protected synchronized void buildObserver(Path path) {

        logger.info("Observing path:" + path.toString());

        FileAlterationObserver observer = new FileAlterationObserver(path.toFile());

        observer.addListener(this);

        observers.put(path, observer);

        this.monitor.addObserver(observer);
    }

    /**
     *
     */
    protected synchronized void syncMonitor() {
        observers.forEach((path, observer)
            -> this.monitor.getObservers().forEach(observer2 -> {
            Path path1 = Paths.get(observer2.getDirectory().toString());
            if (!observers.containsKey(path1)) {
                this.monitor.removeObserver(observer2);
            }
        }));
    }


    @Override
    public void onDirectoryCreate(File directory) {
        buildObserver(Paths.get(directory.toString()));
        syncMonitor();
    }


    @Override
    public void onDirectoryDelete(File directory) {
        observers.remove(Paths.get(directory.toString()));
        syncMonitor();
    }

    @Override
    public void onFileCreate(File file) {

        if (logger.isDebugEnabled()) {
            logger.debug("File Created: " + file);
        }

        triggerBuild(file);
    }

    @Override
    public void onFileChange(File file) {

        if (logger.isDebugEnabled()) {
            logger.debug("File Changed: " + file);
        }

        triggerBuild(file);
    }

    @Override
    public void onFileDelete(File file) {

        if (logger.isDebugEnabled()) {
            logger.debug("File Deleted: " + file);
        }

        triggerBuild(file);
    }

    private void triggerBuild(File file) {
        try {
            for (Callable<Void> task : chain) {
                task.call();
            }
        } catch (Exception e) {
            //ignore
        }
    }

}
