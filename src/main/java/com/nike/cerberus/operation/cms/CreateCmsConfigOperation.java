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

package com.nike.cerberus.operation.cms;

import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;
import com.nike.cerberus.ConfigConstants;
import com.nike.cerberus.command.cms.CreateCmsConfigCommand;
import com.nike.cerberus.domain.cloudformation.BaseOutputs;
import com.nike.cerberus.domain.cloudformation.BaseParameters;
import com.nike.cerberus.domain.cloudformation.VaultParameters;
import com.nike.cerberus.operation.Operation;
import com.nike.cerberus.store.ConfigStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Properties;

import static com.nike.cerberus.ConfigConstants.ADMIN_ROLE_ARN_KEY;
import static com.nike.cerberus.ConfigConstants.CMS_ADMIN_GROUP_KEY;
import static com.nike.cerberus.ConfigConstants.CMS_ROLE_ARN_KEY;
import static com.nike.cerberus.ConfigConstants.JDBC_PASSWORD_KEY;
import static com.nike.cerberus.ConfigConstants.JDBC_URL_KEY;
import static com.nike.cerberus.ConfigConstants.JDBC_USERNAME_KEY;
import static com.nike.cerberus.ConfigConstants.ROOT_USER_ARN_KEY;
import static com.nike.cerberus.ConfigConstants.VAULT_ADDR_KEY;
import static com.nike.cerberus.ConfigConstants.VAULT_TOKEN_KEY;

/**
 * Gathers all of the CMS environment configuration and puts it in the config bucket.
 */
public class CreateCmsConfigOperation implements Operation<CreateCmsConfigCommand> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ConfigStore configStore;

    private final AWSSecurityTokenService securityTokenService;

    @Inject
    public CreateCmsConfigOperation(final ConfigStore configStore,
                                    final AWSSecurityTokenService securityTokenService) {
        this.configStore = configStore;
        this.securityTokenService = securityTokenService;
    }

    @Override
    public void run(final CreateCmsConfigCommand command) {
        configStore.storeCmsAdminGroup(command.getAdminGroup());

        logger.info("Retrieving configuration data from the configuration bucket.");
        final BaseOutputs baseOutputs = configStore.getBaseStackOutputs();
        final BaseParameters baseParameters = configStore.getBaseStackParameters();
        final VaultParameters vaultParameters = configStore.getVaultStackParamters();
        final GetCallerIdentityResult callerIdentity = securityTokenService.getCallerIdentity(
                new GetCallerIdentityRequest());
        final Optional<String> cmsVaultToken = configStore.getCmsVaultToken();
        final Optional<String> cmsDatabasePassword = configStore.getCmsDatabasePassword();

        final Properties cmsConfigMap = new Properties();
        final String rootUserArn = String.format("arn:aws:iam::%s:root", callerIdentity.getAccount());

        cmsConfigMap.put(VAULT_ADDR_KEY, String.format("https://%s", cnameToHost(vaultParameters.getCname())));
        cmsConfigMap.put(VAULT_TOKEN_KEY, cmsVaultToken.get());
        cmsConfigMap.put(CMS_ADMIN_GROUP_KEY, command.getAdminGroup());
        cmsConfigMap.put(ROOT_USER_ARN_KEY, rootUserArn);
        cmsConfigMap.put(ADMIN_ROLE_ARN_KEY, baseParameters.getAccountAdminArn());
        cmsConfigMap.put(CMS_ROLE_ARN_KEY, baseOutputs.getCmsIamRoleArn());
        cmsConfigMap.put(JDBC_URL_KEY, baseOutputs.getCmsDbJdbcConnectionString());
        cmsConfigMap.put(JDBC_USERNAME_KEY, ConfigConstants.DEFAULT_CMS_DB_NAME);
        cmsConfigMap.put(JDBC_PASSWORD_KEY, cmsDatabasePassword.get());

        command.getAdditionalProperties().forEach((k, v) -> {
            if (!cmsConfigMap.containsKey(k)) {
                cmsConfigMap.put(k, v);
            } else {
                logger.warn("Ignoring additional property that would override system configured property, " + k);
            }
        });

        logger.info("Uploading the CMS configuration to the configuration bucket.");
        configStore.storeCmsEnvConfig(cmsConfigMap);

        logger.info("Uploading complete.");
    }

    @Override
    public boolean isRunnable(final CreateCmsConfigCommand command) {
        boolean isRunnable = true;
        final Optional<String> cmsVaultToken = configStore.getCmsVaultToken();
        final Optional<String> cmsDatabasePassword = configStore.getCmsDatabasePassword();

        if (!cmsVaultToken.isPresent()) {
            logger.error("CMS Vault token not present for specified environment.");
            isRunnable = false;
        }

        if (!cmsDatabasePassword.isPresent()) {
            logger.error("CMS database password not present for specified environment.");
            isRunnable = false;
        }

        return isRunnable;
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
}
