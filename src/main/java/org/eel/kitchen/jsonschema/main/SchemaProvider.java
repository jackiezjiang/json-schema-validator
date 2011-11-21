/*
 * Copyright (c) 2011, Francis Galiegue <fgaliegue@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.eel.kitchen.jsonschema.main;

import org.codehaus.jackson.JsonNode;
import org.eel.kitchen.jsonschema.uri.URIHandler;
import org.eel.kitchen.jsonschema.uri.URIHandlerFactory;
import org.eel.kitchen.util.JsonPointer;
import org.eel.kitchen.util.SchemaVersion;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Class used to collect and fetch JSON schemas
 */
public final class SchemaProvider
{
    /**
     * ID used by schemas which do not define {@code id}
     */
    private static final URI ANONYMOUS_ID;

    static {
        try {
            ANONYMOUS_ID = new URI("");
        } catch (URISyntaxException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Factory used to fetch schemas when a JSON Reference is not a JSON Pointer
     */
    private URIHandlerFactory factory;

    /**
     * Map of already collected schemas
     */
    private Map<URI, JsonNode> locators;

    /**
     * ID of the current schema
     */
    private URI currentLocation;

    /**
     * Currently active schema
     */
    private JsonNode schema;

    private SchemaVersion version;

    private SchemaVersion defaultVersion;

    /**
     * Constructor
     *
     * @param schema the initial schema
     * @throws JsonValidationFailureException the initial JSON document is
     * not a schema
     */
    public SchemaProvider(final SchemaVersion defaultVersion,
        final JsonNode schema)
        throws JsonValidationFailureException
    {
        this.defaultVersion = defaultVersion;
        this.schema = schema;
        version = calculateVersion(schema);

        factory = new URIHandlerFactory();
        locators = new HashMap<URI, JsonNode>();

        final URI root;

        try {
            root = schema.path("id").isTextual()
                ? new URI(schema.get("id").getTextValue())
                : ANONYMOUS_ID;
            locators.put(root, schema);
            currentLocation = root;
        } catch (URISyntaxException ignored) {
            // should not happen
        }
    }

    private SchemaProvider(final SchemaVersion defaultVersion)
    {
        this.defaultVersion = defaultVersion;
    }

    /**
     * Spawn a new provider with a subschema of the currently active schema
     *
     * @param pointer the JSON Pointer to locate the subschema
     * @return a new provider
     * @throws JsonValidationFailureException the calculated subschema is not
     * a valid schema
     */
    public SchemaProvider atPoint(final JsonPointer pointer)
        throws JsonValidationFailureException
    {
        final SchemaProvider ret = new SchemaProvider(defaultVersion);
        ret.currentLocation = currentLocation;
        ret.factory = factory;
        ret.locators = locators;

        final JsonNode node = pointer.getPath(locators.get(currentLocation));
        if (node.isMissingNode())
            throw new JsonValidationFailureException("no match in schema for "
                + "path " + pointer);

        ret.schema = node;
        ret.version = calculateVersion(node);
        return ret;
    }

    /**
     * Spawn a new provider with a new active schema
     *
     * @param newschema the schema to use
     * @return the provider
     * @throws JsonValidationFailureException the calculated subschema is not
     * a valid schema
     */
    public SchemaProvider withSchema(final JsonNode newschema)
        throws JsonValidationFailureException
    {
        final SchemaProvider ret = new SchemaProvider(defaultVersion);
        ret.factory = factory;
        ret.locators = locators;
        ret.schema = newschema;
        ret.version = calculateVersion(newschema);
        ret.currentLocation = currentLocation;
        return ret;
    }

    /**
     * Spawn a new provider with a schema located at an URI (typically,
     * an argument to {@code $ref})
     *
     * @param uri the complete URI to the new schema
     * @return the new provider
     * @throws IOException the schema could not be fetched
     * @throws JsonValidationFailureException the calculated subschema is not
     * a valid schema
     */
    public SchemaProvider atURI(final URI uri)
        throws IOException, JsonValidationFailureException
    {
        if (!uri.isAbsolute()) {
            if (!uri.getSchemeSpecificPart().isEmpty())
                throw new IllegalArgumentException("invalid URI: "
                    + "URI is not absolute and is not a JSON Pointer either");
            return this;
        }

        final SchemaProvider ret = new SchemaProvider(defaultVersion);
        ret.factory = factory;
        ret.currentLocation = uri;
        ret.locators = locators;
        if (locators.containsKey(uri)) {
            ret.schema = locators.get(uri);
            ret.version = version;
            return ret;
        }

        ret.schema = factory.getHandler(uri).getDocument(uri);
        ret.version = calculateVersion(ret.schema);
        locators.put(uri, ret.schema);
        return ret;
    }

    /**
     * Return the currently active schema
     *
     * @return the active schema for that provider
     */
    public JsonNode getSchema()
    {
        return schema;
    }

    /**
     * Register a new handler for a specific scheme
     *
     * @param scheme the new scheme
     * @param handler the new handler
     */
    public void registerHandler(final String scheme,
        final URIHandler handler)
    {
        factory.registerHandler(scheme, handler);
    }

    /**
     * Unregister a handler for a scheme
     *
     * @param scheme the scheme
     */
    public void unregisterHandler(final String scheme)
    {
        factory.unregisterHandler(scheme);
    }

    public void setDefaultVersion(final SchemaVersion defaultVersion)
    {
        this.defaultVersion = defaultVersion;
    }

    public SchemaVersion getVersion()
    {
        return version;
    }

    private SchemaVersion calculateVersion(final JsonNode schema)
        throws JsonValidationFailureException
    {
        if (schema == null)
            throw new JsonValidationFailureException("schema is null");

        if (!schema.isObject())
            throw new JsonValidationFailureException("not a schema (not an "
                + "object)");

        final SchemaVersion ret = SchemaVersion.getVersion(schema);

        return ret == null ? defaultVersion : ret;
    }
}