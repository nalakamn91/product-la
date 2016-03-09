/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.la.alert.util;

import org.wso2.carbon.event.publisher.core.EventPublisherService;
import org.wso2.carbon.event.stream.core.EventStreamService;
import org.wso2.carbon.ntask.core.service.TaskService;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.registry.core.service.TenantRegistryLoader;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;

public class LAAlertServiceValueHolder {

    private static volatile LAAlertServiceValueHolder laAlertServiceValueHolder;
    private TaskService taskService;
    private EventPublisherService eventPublisherService;
    private EventStreamService eventStreamService;
    private RegistryService registryService;
    private TenantRegistryLoader tenantRegistryLoader;

    public static LAAlertServiceValueHolder getInstance() {
        if (laAlertServiceValueHolder == null) {
            synchronized (LAAlertServiceValueHolder.class) {
                if (laAlertServiceValueHolder == null) {
                    laAlertServiceValueHolder = new LAAlertServiceValueHolder();
                }
            }
        }
        return laAlertServiceValueHolder;
    }

    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    public TaskService getTaskService() {
        return taskService;
    }

    public void setEventPublisherService(EventPublisherService eventPublisherService) {
        this.eventPublisherService = eventPublisherService;
    }

    public EventPublisherService getEventPublisherService() {
        return eventPublisherService;
    }

    public void setEventStreamService(EventStreamService eventStreamService) {
        this.eventStreamService = eventStreamService;
    }

    public EventStreamService getEventStreamService() {
        return eventStreamService;
    }

    public  void setRegistryService(RegistryService registryService) {
        this.registryService = registryService;
    }

    public  void setTenantRegistryLoader(TenantRegistryLoader tenantRegistryLoader) {
        this.tenantRegistryLoader = tenantRegistryLoader;
    }

    public UserRegistry getTenantConfigRegistry(int tenantId) throws RegistryException {
        if (tenantId == MultitenantConstants.SUPER_TENANT_ID) {
            return this.registryService.getConfigSystemRegistry();
        } else {
            this.tenantRegistryLoader.loadTenantRegistry(tenantId);
            return this.registryService.getConfigSystemRegistry(tenantId);
        }
    }


}
