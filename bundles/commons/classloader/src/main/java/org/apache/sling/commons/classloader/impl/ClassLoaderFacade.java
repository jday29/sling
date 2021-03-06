/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.commons.classloader.impl;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The <code>ClassLoaderFacade</code> is a facade
 * for the dynamic class loading.
 * This class loader is returned to the clients of the
 * dynamic class loader manager.
 * This class loader delegates to other class loaders
 * but caches its result for performance.
 */
public class ClassLoaderFacade extends ClassLoader {

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    /** Dynamic class loader manager which manages the dynamic class loader providers for this facade. */
    private final DynamicClassLoaderManagerImpl manager;

    /**
     * Constructor
     */
    public ClassLoaderFacade(final DynamicClassLoaderManagerImpl manager) {
        this.manager = manager;
    }

    /**
     * @see java.lang.ClassLoader#getResource(java.lang.String)
     */
    public URL getResource(String name) {
        if ( !this.manager.isActive() ) {
            logger.error("Dynamic class loader has already been deactivated.");
            return null;
        }
        final ClassLoader[] loaders = manager.getDynamicClassLoaders();
        for(final ClassLoader cl : loaders) {
            if ( cl != null ) {
                try {
                    final URL u = cl.getResource(name);
                    if ( u != null ) {
                        return u;
                    }
                } catch (Throwable t) {
                    logger.error("Exception while querying class loader " + cl + " for resource " + name, t);
                }
            }
        }
        return null;
    }

    private final List<URL> EMPTY_LIST = Collections.emptyList();

    /**
     * @see java.lang.ClassLoader#getResources(java.lang.String)
     */
    public Enumeration<URL> getResources(String name) throws IOException {
        if ( !this.manager.isActive() ) {
            logger.error("Dynamic class loader has already been deactivated.");
            return Collections.enumeration(EMPTY_LIST);
        }
        final ClassLoader[] loaders = manager.getDynamicClassLoaders();
        final List<URL> resources = new ArrayList<URL>();
        for(final ClassLoader cl : loaders) {
            if ( cl != null ) {
                try {
                    final Enumeration<URL> e = cl.getResources(name);
                    if ( e != null && e.hasMoreElements() ) {
                        resources.addAll(Collections.list(e));
                    }
                } catch (Throwable t) {
                    logger.error("Exception while querying class loader " + cl + " for resources " + name, t);
                }
            }
        }
        return Collections.enumeration(resources);
    }

    /**
     * @see java.lang.ClassLoader#loadClass(java.lang.String, boolean)
     */
    protected synchronized Class<?> loadClass(String name, boolean resolve)
    throws ClassNotFoundException {
        if ( !this.manager.isActive() ) {
            logger.error("Dynamic class loader has already been deactivated.");
            throw new ClassNotFoundException(name);
        }
        final ClassLoader[] loaders = manager.getDynamicClassLoaders();
        for(final ClassLoader cl : loaders) {
            if ( cl != null ) {
                try {
                    final Class<?> c = cl.loadClass(name);
                    return c;
                } catch (ClassNotFoundException cnfe) {
                    // we just ignore this and try the next class loader
                } catch (Throwable t) {
                    logger.error("Exception while trying to load class " + name + " from class loader " + cl, t);
                }
            }
        }
        throw new ClassNotFoundException(name);
    }
}
