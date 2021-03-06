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
package org.apache.sling.jcr.resource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.StringTokenizer;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.jcr.resource.internal.helper.LazyInputStream;

/**
 * The <code>JcrResourceUtil</code> class provides helper methods used
 * throughout this bundle.
 */
public class JcrResourceUtil {

    /** Helper method to execute a JCR query */
    public static QueryResult query(Session session, String query,
            String language) throws RepositoryException {
        QueryManager qManager = session.getWorkspace().getQueryManager();
        Query q = qManager.createQuery(query, language);
        return q.execute();
    }

    /** Converts a JCR Value to a corresponding Java Object */
    public static Object toJavaObject(Value value) throws RepositoryException {
        switch (value.getType()) {
            case PropertyType.DECIMAL:
                return value.getDecimal();
            case PropertyType.BINARY:
                return new LazyInputStream(value);
            case PropertyType.BOOLEAN:
                return value.getBoolean();
            case PropertyType.DATE:
                return value.getDate();
            case PropertyType.DOUBLE:
                return value.getDouble();
            case PropertyType.LONG:
                return value.getLong();
            case PropertyType.NAME: // fall through
            case PropertyType.PATH: // fall through
            case PropertyType.REFERENCE: // fall through
            case PropertyType.STRING: // fall through
            case PropertyType.UNDEFINED: // not actually expected
            default: // not actually expected
                return value.getString();
        }
    }

    /**
     * Converts the value(s) of a JCR Property to a corresponding Java Object.
     * If the property has multiple values the result is an array of Java
     * Objects representing the converted values of the property.
     */
    public static Object toJavaObject(Property property)
            throws RepositoryException {
        // multi-value property: return an array of values
        if (property.isMultiple()) {
            Value[] values = property.getValues();
            Object[] result = new Object[values.length];
            for (int i = 0; i < values.length; i++) {
                Value value = values[i];
                if (value != null) {
                    result[i] = toJavaObject(value);
                }
            }
            return result;
        }

        // single value property
        return toJavaObject(property.getValue());
    }

    /**
     * Creates a {@link javax.jcr.Value JCR Value} for the given object with
     * the given Session.
     * Selects the the {@link javax.jcr.PropertyType PropertyType} according
     * the instance of the object's Class
     *
     * @param value object
     * @param session to create value for
     * @return the value or null if not convertible to a valid PropertyType
     * @throws RepositoryException in case of error, accessing the Repository
     */
    public static Value createValue(Object value, Session session)
            throws RepositoryException {
        Value val;
        ValueFactory fac = session.getValueFactory();
        if(value instanceof Calendar) {
            val = fac.createValue((Calendar)value);
        } else if (value instanceof InputStream) {
            val = fac.createValue(fac.createBinary((InputStream)value));
        } else if (value instanceof Node) {
            val = fac.createValue((Node)value);
        } else if (value instanceof BigDecimal) {
            val = fac.createValue((BigDecimal)value);
        } else if (value instanceof Long) {
            val = fac.createValue((Long)value);
        } else if (value instanceof Number) {
            val = fac.createValue(((Number)value).doubleValue());
        } else if (value instanceof Boolean) {
            val = fac.createValue((Boolean) value);
        } else if ( value instanceof String ){
            val = fac.createValue((String)value);
        } else {
            val = null;
        }
        return val;
    }

    /**
     * Sets the value of the property.
     * Selects the {@link javax.jcr.PropertyType PropertyType} according
     * to the instance of the object's class.
     * @param node         The node where the property will be set on.
     * @param propertyName The name of the property.
     * @param propertyValue The value for the property.
     */
    public static void setProperty(final Node node,
                                   final String propertyName,
                                   final Object propertyValue)
    throws RepositoryException {
        if ( propertyValue == null ) {
            node.setProperty(propertyName, (String)null);
        } else if ( propertyValue.getClass().isArray() ) {
            final Object[] values = (Object[])propertyValue;
            final Value[] setValues = new Value[values.length];
            for(int i=0; i<values.length; i++) {
                setValues[i] = createValue(values[i], node.getSession());
            }
            node.setProperty(propertyName, setValues);
        } else {
            node.setProperty(propertyName, createValue(propertyValue, node.getSession()));
        }
    }

    /**
     * Helper method, which returns the given resource type as returned from the
     * {@link org.apache.sling.api.resource.Resource#getResourceType()} as a
     * relative path.
     *
     * @param type The resource type to be converted into a path
     * @return The resource type as a path.
     * @deprecated Use {@link ResourceUtil#resourceTypeToPath(String)}
     */
    @Deprecated
    public static String resourceTypeToPath(String type) {
        return type.replaceAll("\\:", "/");
    }

    /**
     * Returns the super type of the given resource type. This is the result of
     * adapting the child resource
     * {@link JcrResourceConstants#SLING_RESOURCE_SUPER_TYPE_PROPERTY} of the
     * <code>Resource</code> addressed by the <code>resourceType</code> to a
     * string. If no such child resource exists or if the resource does not
     * adapt to a string, this method returns <code>null</code>.
     *
     * @param resourceResolver The <code>ResourceResolver</code> used to
     *            access the resource whose path (relative or absolute) is given
     *            by the <code>resourceType</code> parameter.
     * @param resourceType The resource type whose super type is to be returned.
     *            This type is turned into a path by calling the
     *            {@link #resourceTypeToPath(String)} method before trying to
     *            get the resource through the <code>resourceResolver</code>.
     * @return the super type of the <code>resourceType</code> or
     *         <code>null</code> if the resource type does not have a child
     *         resource
     *         {@link JcrResourceConstants#SLING_RESOURCE_SUPER_TYPE_PROPERTY}
     *         adapting to a string.
     * @deprecated Use {@link ResourceUtil#getResourceSuperType(ResourceResolver, String)}
     */
    @Deprecated
    public static String getResourceSuperType(
            ResourceResolver resourceResolver, String resourceType) {
        return ResourceUtil.getResourceSuperType(resourceResolver, resourceType);
    }

    /**
     * Returns the resource super type of the given resource. This is either the
     * child resource
     * {@link JcrResourceConstants#SLING_RESOURCE_SUPER_TYPE_PROPERTY} if the
     * given <code>resource</code> adapted to a string or the result of
     * calling the {@link #getResourceSuperType(ResourceResolver, String)}
     * method on the resource type of the <code>resource</code>.
     * <p>
     * This mechanism allows to specifically set the resource super type on a
     * per-resource level overwriting any resource super type hierarchy
     * pre-defined by the actual resource type of the resource.
     *
     * @param resource The <code>Resource</code> whose resource super type is
     *            requested.
     * @return The resource super type or <code>null</code> if the algorithm
     *         described above does not yield a resource super type.
     * @deprecated Call {@link ResourceUtil#findResourceSuperType(Resource)}
     */
    @Deprecated
    public static String getResourceSuperType(Resource resource) {
        String resourceSuperType = resource.getResourceSuperType();
        if ( resourceSuperType == null ) {
            final ResourceResolver resolver = resource.getResourceResolver();

            // try explicit resourceSuperType resource
            final String resourceType = resource.getResourceType();
            resourceSuperType = ResourceUtil.getResourceSuperType(resolver, resourceType);
        }

        return resourceSuperType;
    }

    /**
     * Creates or gets the {@link javax.jcr.Node Node} at the given Path.
     * In case it has to create the Node all non-existent intermediate path-elements
     * will be create with the given intermediate node type and the returned node
     * will be created with the given nodeType
     *
     * @param path to create
     * @param intermediateNodeType to use for creation of intermediate nodes (or null)
     * @param nodeType to use for creation of the final node (or null)
     * @param session to use
     * @param autoSave Should save be called when a new node is created?
     * @return the Node at path
     * @throws RepositoryException in case of exception accessing the Repository
     */
    public static Node createPath(String path,
                                  String intermediateNodeType,
                                  String nodeType,
                                  Session session,
                                  boolean autoSave)
    throws RepositoryException {
        if (path == null || path.length() == 0 || "/".equals(path)) {
            return session.getRootNode();
        } else if (!session.itemExists(path)) {
            return createPath(session.getRootNode(),
                    path.substring(1),
                    intermediateNodeType,
                    nodeType,
                    autoSave);
        } else {
            return (Node) session.getItem(path);
        }
    }

    /**
     * Creates or gets the {@link javax.jcr.Node Node} at the given Path.
     * In case it has to create the Node all non-existent intermediate path-elements
     * will be create with the given intermediate node type and the returned node
     * will be created with the given nodeType
     *
     * @param parentNode starting node
     * @param relativePath to create
     * @param intermediateNodeType to use for creation of intermediate nodes (or null)
     * @param nodeType to use for creation of the final node (or null)
     * @param autoSave Should save be called when a new node is created?
     * @return the Node at path
     * @throws RepositoryException in case of exception accessing the Repository
     */
    public static Node createPath(Node   parentNode,
                                  String relativePath,
                                  String intermediateNodeType,
                                  String nodeType,
                                  boolean autoSave)
    throws RepositoryException {
        if (relativePath == null || relativePath.length() == 0 || "/".equals(relativePath)) {
            return parentNode;
        } else if (!parentNode.hasNode(relativePath)) {
            Node node = parentNode;
            int pos = relativePath.lastIndexOf('/');
            if ( pos != -1 ) {
                final StringTokenizer st = new StringTokenizer(relativePath.substring(0, pos), "/");
                while ( st.hasMoreTokens() ) {
                    final String token = st.nextToken();
                    if ( !node.hasNode(token) ) {
                        try {
                            if ( intermediateNodeType != null ) {
                                node.addNode(token, intermediateNodeType);
                            } else {
                                node.addNode(token);
                            }
                            if ( autoSave ) node.getSession().save();
                        } catch (RepositoryException re) {
                            // we ignore this as this folder might be created from a different task
                            node.refresh(false);
                        }
                    }
                    node = node.getNode(token);
                }
                relativePath = relativePath.substring(pos + 1);
            }
            if ( !node.hasNode(relativePath) ) {
                if ( nodeType != null ) {
                    node.addNode(relativePath, nodeType);
                } else {
                    node.addNode(relativePath);
                }
                if ( autoSave ) node.getSession().save();
            }
            return node.getNode(relativePath);
        } else {
            return parentNode.getNode(relativePath);
        }
    }
}
