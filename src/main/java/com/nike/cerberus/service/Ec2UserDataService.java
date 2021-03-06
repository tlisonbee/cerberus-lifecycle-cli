/*
 * Copyright (c) 2016 Nike, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nike.cerberus.service;

import com.google.common.collect.Maps;
import com.nike.cerberus.ConfigConstants;
import com.nike.cerberus.domain.EnvironmentMetadata;
import com.nike.cerberus.domain.environment.StackName;
import com.nike.cerberus.store.ConfigStore;
import org.apache.commons.io.IOUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Map;

/**
 * Service for generating EC2 user data for Cerberus instances.
 */
public class Ec2UserDataService {

    private static final String nginxWriteResolverConfPath = "/write-nginx-resolver-conf";

    private final EnvironmentMetadata environmentMetadata;

    private final ConfigStore configStore;

    @Inject
    public Ec2UserDataService(final EnvironmentMetadata environmentMetadata,
                              final ConfigStore configStore) {
        this.environmentMetadata = environmentMetadata;
        this.configStore = configStore;
    }

    public String getUserData(final StackName stackName, final String ownerGroup) {
        switch (stackName) {
            case CMS:
                return getCmsUserData(ownerGroup);
            case GATEWAY:
                return getGatewayUserData(ownerGroup);
            case VAULT:
            case CONSUL:
                return getConsulAndVaultUserData(stackName, ownerGroup);
            default:
                throw new IllegalArgumentException("The stack specified does not support user data. stack: "
                        + stackName.getName());
        }
    }

    private String getGatewayUserData(final String ownerGroup) {
        final Map<String, String> userDataMap = Maps.newHashMap();
        addStandardEnvironmentVariables(userDataMap, StackName.GATEWAY.getName(), ownerGroup);

        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(writeExportEnvVars(userDataMap));
        stringBuilder.append(getFileContentsFromClasspath(nginxWriteResolverConfPath));
        return encodeUserData(stringBuilder.toString());
    }

    private String getCmsUserData(final String ownerGroup) {
        final Map<String, String> userDataMap = Maps.newHashMap();
        addStandardEnvironmentVariables(userDataMap, StackName.CMS.getName(), ownerGroup);

        return encodeUserData(writeExportEnvVars(userDataMap));
    }

    private String getConsulAndVaultUserData(final StackName stackName, final String ownerGroup) {
        final Map<String, String> userDataMap = Maps.newHashMap();
        addStandardEnvironmentVariables(userDataMap, stackName.getName(), ownerGroup);

        userDataMap.put("CONSUL_DC", ConfigConstants.CONSUL_DATACENTER);

        return encodeUserData(writeExportEnvVars(userDataMap));
    }

    private void addStandardEnvironmentVariables(final Map<String, String> userDataMap,
                                                 final String appName,
                                                 final String ownerGroup) {
        userDataMap.put("CLOUD_ENVIRONMENT", ConfigConstants.ENV_PREFIX + environmentMetadata.getName());
        userDataMap.put("CLOUD_MONITOR_BUCKET", appName);
        userDataMap.put("CLOUD_APP", appName);
        userDataMap.put("CLOUD_APP_GROUP", ownerGroup);
        userDataMap.put("CLOUD_CLUSTER", appName);
        userDataMap.put("CLASSIFICATION", "Gold");
        userDataMap.put("EC2_REGION", environmentMetadata.getRegionName());
        userDataMap.put("AWS_REGION", environmentMetadata.getRegionName());
        userDataMap.put("CONFIG_S3_BUCKET", environmentMetadata.getBucketName());
        userDataMap.put("CONFIG_KEY_ID", configStore.getBaseStackOutputs().getConfigFileKeyId());
    }

    private String writeExportEnvVars(Map<String, String> userDataMap) {
        StringBuilder stringBuilder = new StringBuilder();

        for (Map.Entry<String, String> entry : userDataMap.entrySet()) {
            stringBuilder.append(String.format("export %s=\"%s\"%n", entry.getKey(), entry.getValue()));
        }

        stringBuilder.append("\n");
        return stringBuilder.toString();
    }

    private String encodeUserData(String userData) {
        return Base64.getEncoder().encodeToString(userData.getBytes(Charset.forName("UTF-8")));
    }

    /**
     * Removes the final '.' from the CNAME.
     *
     * @param cname The cname to convert
     * @return The host derived from the CNAME
     */
    private String cnameToHost(final String cname) {
        return cname.substring(0, cname.length() - 1);
    }

    /**
     * Reads the contents of a file on the classpath.
     *
     * @param path Path to the resource
     * @return Contents of resource
     */
    private String getFileContentsFromClasspath(final String path) {
        try {
            return IOUtils.toString(getClass().getResourceAsStream(path), ConfigConstants.DEFAULT_ENCODING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read file that should be in classpath!", e);
        }
    }
}
