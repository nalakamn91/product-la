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

package org.wso2.carbon.la.alert.impl;

import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.databridge.commons.AttributeType;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.databridge.commons.exception.MalformedStreamDefinitionException;
import org.wso2.carbon.event.publisher.core.EventPublisherService;
import org.wso2.carbon.event.publisher.core.exception.EventPublisherConfigurationException;
import org.wso2.carbon.event.stream.core.EventStreamService;
import org.wso2.carbon.event.stream.core.exception.EventStreamConfigurationException;
import org.wso2.carbon.la.alert.domain.LAAlertConstant;
import org.wso2.carbon.la.alert.domain.SATaskInfo;
import org.wso2.carbon.la.alert.util.LAAlertServiceValueHolder;
import org.wso2.carbon.ntask.common.TaskException;
import org.wso2.carbon.ntask.core.TaskInfo;
import org.wso2.carbon.ntask.core.TaskManager;
import org.wso2.carbon.la.alert.domain.config.*;
import org.wso2.carbon.registry.core.Collection;
import org.wso2.carbon.registry.core.RegistryConstants;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.session.UserRegistry;
import org.wso2.carbon.registry.core.utils.RegistryUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScheduleAlertControllerImpl implements ScheduleAlertController {

    private static final Log log = LogFactory.getLog(ScheduleAlertControllerImpl.class);

    public void registerScheduleAlertTask(SATaskInfo saTaskInfo,String userName, int tenantId) {
        try {
            try {
                this.createOutputStream(saTaskInfo.getAlertName());
                this.createPublisher(saTaskInfo.getAlertName(),saTaskInfo.getAlertActionType(), saTaskInfo.getAlertActionProperties());
            } catch (EventPublisherConfigurationException e) {
                e.printStackTrace();
            } catch (EventStreamConfigurationException e) {
                e.printStackTrace();
            } catch (MalformedStreamDefinitionException e) {
                e.printStackTrace();
            }

            this.saveConfiguration(saTaskInfo,tenantId);

            TaskManager taskManager = LAAlertServiceValueHolder.getInstance().getTaskService().getTaskManager(LAAlertConstant.SCHEDULE_ALERT_TASK_TYPE);
            TaskInfo scheduleTaskInfo=createScheduleAlertTask(saTaskInfo,userName);
            taskManager.registerTask(scheduleTaskInfo);
            taskManager.rescheduleTask(scheduleTaskInfo.getName());

        }
        catch (TaskException e){
            log.error("Unable to get task manager instance for SCHEDULE ALERT TASK" + e.getMessage(), e);
        }
    }

    public TaskInfo createScheduleAlertTask(SATaskInfo saTaskInfo, String userName) {
        String taskName=saTaskInfo.getAlertName();
        TaskInfo.TriggerInfo triggerInfo=new TaskInfo.TriggerInfo(saTaskInfo.getCronExpression());
        Map<String,String> taskProperties=new HashMap<>();
        taskProperties.put(LAAlertConstant.TABLE_NAME,saTaskInfo.getTableName());
        taskProperties.put(LAAlertConstant.QUERY,saTaskInfo.getQuery());
        taskProperties.put(LAAlertConstant.USER_NAME,userName);
        taskProperties.put(LAAlertConstant.TIME_FROM,String.valueOf(saTaskInfo.getTimeFrom()));
        taskProperties.put(LAAlertConstant.TIME_TO,String.valueOf(saTaskInfo.getTimeTo()));
        taskProperties.put(LAAlertConstant.START,String.valueOf(saTaskInfo.getStart()));
        taskProperties.put(LAAlertConstant.LENGTH,String.valueOf(saTaskInfo.getLength()));
        taskProperties.put(LAAlertConstant.ALERT_NAME,saTaskInfo.getAlertName());
        return new TaskInfo(taskName, ScheduleAlertTask.class.getName(), taskProperties, triggerInfo);

    }

    private boolean createPublisher(String alertName, String alertActionType ,Map<String,String> alertActionProperties) throws EventPublisherConfigurationException {
        EventPublisherService eventPublisherService=LAAlertServiceValueHolder.getInstance().getEventPublisherService();
        EventPublisher eventPublisher=new EventPublisher();
        eventPublisher.setName(alertName);
        eventPublisher.setStatistics("disable");
        eventPublisher.setTrace("disable");

        From from=new From();
        from.setStreamName(alertName);
        from.setVersion("1.0.0");

        Mapping mapping=new Mapping();
        mapping.setCustomMapping("enable");
        mapping.setType("text");
        mapping.setInline(alertActionProperties.get("message"));

        To to=new To();
        to.setEventAdapterType(alertActionType);

        ArrayList<Property> properties=new ArrayList<Property>();
        Property pro1=new Property();
        for(Map.Entry<String,String> prop: alertActionProperties.entrySet()){
            if (prop.getKey()!="message") {
                pro1.setName(prop.getKey());
                pro1.setValue(prop.getValue());
            }
        }

        properties.add(pro1);
        to.setProperty(properties);

        eventPublisher.setFrom(from);
        eventPublisher.setMapping(mapping);
        eventPublisher.setTo(to);

        StringWriter stringWriter=new StringWriter();

        try {

            // create JAXB context and initializing Marshaller
            JAXBContext jaxbContext = JAXBContext.newInstance(EventPublisher.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();

            // for getting nice formatted output
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

            //specify the location and name of xml file to be created
            // File XMLfile = new File("C:\\arpit\\CountryRecord.xml");

            // Writing to XML file
            //  jaxbMarshaller.marshal(countryIndia, XMLfile);
            // Writing to console
            jaxbMarshaller.marshal(eventPublisher,stringWriter);

        } catch (JAXBException e) {
            // some exception occured
            e.printStackTrace();
        }

         eventPublisherService.deployEventPublisherConfiguration(stringWriter.toString());
        return  true;
    }

    private boolean createOutputStream(String alertName) throws MalformedStreamDefinitionException, EventStreamConfigurationException {
        EventStreamService eventStreamService=LAAlertServiceValueHolder.getInstance().getEventStreamService();
        StreamDefinition streamDefinition=new StreamDefinition(alertName,"1.0.0");
        streamDefinition.addPayloadData("count", AttributeType.LONG);
        eventStreamService.addEventStreamDefinition(streamDefinition);
        return true;
    }

    private void createConfigurationCollection(UserRegistry userRegistry) throws RegistryException {
        if (!userRegistry.resourceExists(LAAlertConstant.ALERT_CONFIGURATION_LOCATION)) {
            Collection collection = userRegistry.newCollection();
            userRegistry.put(LAAlertConstant.ALERT_CONFIGURATION_LOCATION, collection);
        }
    }

    private String getConfigurationLocation(String alertName) {
        return LAAlertConstant.ALERT_CONFIGURATION_LOCATION + RegistryConstants.PATH_SEPARATOR + alertName +
                LAAlertConstant.CONFIGURATION_EXTENSION_SEPARATOR + LAAlertConstant.CONFIGURATION_EXTENSION;
    }

    private void saveConfiguration(SATaskInfo saTaskInfo,int tenantId) {
        try {
            UserRegistry userRegistry=LAAlertServiceValueHolder.getInstance().getTenantConfigRegistry(tenantId);
            createConfigurationCollection(userRegistry);
            String configurationLocation=getConfigurationLocation(saTaskInfo.getAlertName());
            if (!userRegistry.resourceExists(configurationLocation)) {
                Resource resource = userRegistry.newResource();
                Gson gson=new Gson();
                String json=gson.toJson(saTaskInfo);
                resource.setContent(json);
                resource.setMediaType(LAAlertConstant.CONFIGURATION_MEDIA_TYPE);
                userRegistry.put(configurationLocation, resource);
            }

        } catch (RegistryException e) {
            e.printStackTrace();
        }
    }

    public List<SATaskInfo> getAllAlertConfigurations(int tenantId){
        try {
            UserRegistry userRegistry=LAAlertServiceValueHolder.getInstance().getTenantConfigRegistry(tenantId);
            createConfigurationCollection(userRegistry);
            Collection configurationCollection=(Collection)userRegistry.get(LAAlertConstant.ALERT_CONFIGURATION_LOCATION);
            String[] configs=configurationCollection.getChildren();
            if (configs != null) {
                List<SATaskInfo> configurations=new ArrayList<>();
                for (String conf: configs) {
                    String content= RegistryUtils.decodeBytes((byte[]) userRegistry.get(conf).getContent());
                    configurations.add(getConfigurationContent(content));
                }
                return configurations;
            }
        } catch (RegistryException e) {
            e.printStackTrace();
        }
        return null;
    }

    private SATaskInfo getConfigurationContent(String content) {
        Gson gson=new Gson();
        SATaskInfo saTaskInfo=gson.fromJson(content,SATaskInfo.class);
        return saTaskInfo;
    }

    public boolean deleteAlertTask(String alertName, int tenantId) throws TaskException, RegistryException {
        TaskManager taskManager = LAAlertServiceValueHolder.getInstance().getTaskService().getTaskManager(LAAlertConstant.SCHEDULE_ALERT_TASK_TYPE);
        taskManager.deleteTask(alertName);
        UserRegistry userRegistry = LAAlertServiceValueHolder.getInstance().getTenantConfigRegistry(tenantId);
        String fileLocation=getConfigurationLocation(alertName);
        if (userRegistry.resourceExists(fileLocation)) {
            userRegistry.delete(fileLocation);
        } else {
            log.info("Cannot delete non existing file : " + alertName + " for tenantId : " + tenantId + ". " +
                    "It might have been deleted already." );
        }

        return true;
    }
}

