/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016-2018 Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 */

package ai.grakn.bootup.config;

import ai.grakn.GraknConfigKey;
import ai.grakn.engine.GraknConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;

/**
 * Container class for storing and manipulating storage configuration.
 *
 * @author Kasper Piskorski
 */
public class StorageConfig extends ProcessConfig<Object> {

    private static final String EMPTY_VALUE = "";
    private static final String CONFIG_PARAM_PREFIX = "storage.internal.";
    private static final String SAVED_CACHES_SUBDIR = "cassandra/saved_caches";
    private static final String COMMITLOG_SUBDIR = "cassandra/commitlog";
    private static final String DATA_SUBDIR = "cassandra/data";

    private StorageConfig(Map<String, Object> yamlParams){ super(yamlParams); }

    public static StorageConfig of(String yaml) { return new StorageConfig(StorageConfig.parseStringToMap(yaml)); }
    public static StorageConfig from(Path configPath){
        String configString = ConfigProcessor.getConfigStringFromFile(configPath);
        return of(configString);
    }

    private static Map<String, Object> parseStringToMap(String yaml){
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        try {
            TypeReference<Map<String, Object>> reference = new TypeReference<Map<String, Object>>(){};
            Map<String, Object> yamlParams = mapper.readValue(yaml, reference);
            return Maps.transformValues(yamlParams, value -> value == null ? EMPTY_VALUE : value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    Map.Entry<String, Object> propToEntry(String key, String value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    @Override
    public String toConfigString() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory().enable(YAMLGenerator.Feature.MINIMIZE_QUOTES));
        try {
            ByteArrayOutputStream outputstream = new ByteArrayOutputStream();
            mapper.writeValue(outputstream, params());
            return outputstream.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private StorageConfig updateDirs(GraknConfig config) {
        String dbDir = config.getProperty(GraknConfigKey.DATA_DIR);

        ImmutableMap<String, Object> dirParams = ImmutableMap.of(
                "data_file_directories", Collections.singletonList(dbDir + DATA_SUBDIR),
                "saved_caches_directory", dbDir + SAVED_CACHES_SUBDIR,
                "commitlog_directory", dbDir + COMMITLOG_SUBDIR
        );
        return new StorageConfig(this.updateParamsFromMap(dirParams));
    }

    @Override
    Map<String, Object> updateParamsFromConfig(String CONFIG_PARAM_PREFIX, GraknConfig config) {
        //overwrite params with params from grakn config
        Map<String, Object> updatedParams = Maps.newHashMap(params());
        config.properties()
                .stringPropertyNames()
                .stream()
                .filter(prop -> prop.contains(CONFIG_PARAM_PREFIX))
                .forEach(prop -> {
                    String param = prop.replaceAll(CONFIG_PARAM_PREFIX, "");
                    if (updatedParams.containsKey(param)) {
                        updatedParams.put(param, config.properties().getProperty(prop));
                    }
                });
        return updatedParams;
    }

    @Override
    public StorageConfig updateGenericParams(GraknConfig config) {
        return new StorageConfig(this.updateParamsFromConfig(CONFIG_PARAM_PREFIX, config));
    }

    @Override
    public StorageConfig updateFromConfig(GraknConfig config){
        return this
                .updateGenericParams(config)
                .updateDirs(config);
    }

}
