/*
 * Copyright 2026 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.conductoross.conductor.webhook;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.netflix.conductor.common.config.ObjectMapperProvider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;

/**
 * Computes hashes from incoming webhook payloads for matching against waiting tasks.
 *
 * <p>The hash is computed by extracting values from the webhook body using JSONPath expressions
 * defined in the webhook's matchers configuration. The extracted values are concatenated with the
 * webhook identifier to form a unique hash that can be matched against tasks waiting for specific
 * webhook events.
 */
@Service
public class WebhookHashingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookHashingService.class);

    public static final String WEBHOOK_DELIMITER = ";";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapperProvider().getObjectMapper();

    /**
     * Computes a hash from the webhook body using the provided matchers.
     *
     * <p>The hash is built by:
     *
     * <ol>
     *   <li>Parsing the body as JSON
     *   <li>Merging request parameters into the parsed object
     *   <li>For each matcher path (in sorted order), extracting the value via JSONPath
     *   <li>Appending each value to the hash with a delimiter
     * </ol>
     *
     * @param hash the StringBuilder containing the base hash (typically
     *     webhookId;workflowName;version;taskRef)
     * @param matches the matcher configuration mapping JSONPath expressions to expected values
     * @param body the raw webhook body as a JSON string
     * @param parameters additional request parameters to merge into the payload
     * @return the computed hash string, or null if any matcher path is not found or doesn't match
     */
    @SuppressWarnings("unchecked")
    public String computeJsonHash(
            StringBuilder hash,
            Map<String, Object> matches,
            String body,
            Map<String, Object> parameters) {

        Map<String, Object> map;
        try {
            Object obj = OBJECT_MAPPER.readValue(body, Object.class);
            if (obj instanceof Map) {
                map = (Map<String, Object>) obj;
            } else {
                map = new HashMap<>();
                map.put("request", obj);
            }
        } catch (JsonProcessingException e) {
            LOGGER.debug("Failed to parse webhook body as JSON: {}", e.getMessage());
            map = new HashMap<>();
        }

        // Merge request parameters into the payload
        if (parameters != null) {
            map.putAll(parameters);
        }

        DocumentContext jsonContext = JsonPath.parse(map);

        // Process matchers in sorted order for deterministic hashing
        TreeSet<String> pathsToCheck = new TreeSet<>(matches.keySet());
        for (String path : pathsToCheck) {
            Object value = extract(jsonContext, path);
            if (value == null) {
                return null;
            }

            // Handle JSONArray results (JSONPath can return arrays)
            if (value instanceof JSONArray) {
                JSONArray valueArray = (JSONArray) value;
                if (valueArray.isEmpty()) {
                    return null;
                }
                value = valueArray.get(0).toString();
            }

            String expectedValue = Objects.toString(matches.get(path));

            // If expected value starts with $, it's a variable reference - just use the actual
            // value
            // Otherwise, the actual value must match the expected value (case-insensitive trim)
            if (expectedValue.startsWith("$")
                    || expectedValue.trim().equalsIgnoreCase(value.toString().trim())) {
                hash.append(WEBHOOK_DELIMITER).append(value);
            } else {
                return null;
            }
        }

        return hash.toString();
    }

    private Object extract(DocumentContext jsonContext, String path) {
        try {
            return jsonContext.read(path);
        } catch (Exception e) {
            LOGGER.warn("Exception reading path {} - {}", path, e.getMessage());
            return null;
        }
    }
}
