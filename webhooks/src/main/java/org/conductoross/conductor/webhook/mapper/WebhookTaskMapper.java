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
package org.conductoross.conductor.webhook.mapper;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.netflix.conductor.core.exception.TerminateWorkflowException;
import com.netflix.conductor.core.execution.mapper.TaskMapper;
import com.netflix.conductor.core.execution.mapper.TaskMapperContext;
import com.netflix.conductor.model.TaskModel;

import static org.conductoross.conductor.service.webhook.WebhookTaskService.Constants.WAIT_FOR_WEBHOOK;

/** Maps a WAIT_FOR_WEBHOOK task definition to an executable TaskModel. */
@Component
public class WebhookTaskMapper implements TaskMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookTaskMapper.class);

    @Override
    public String getTaskType() {
        return WAIT_FOR_WEBHOOK;
    }

    @Override
    public List<TaskModel> getMappedTasks(TaskMapperContext taskMapperContext)
            throws TerminateWorkflowException {

        LOGGER.debug("TaskMapperContext {} in WebhookTaskMapper", taskMapperContext);

        Map<String, Object> taskInput = taskMapperContext.getTaskInput();
        int retryCount = taskMapperContext.getRetryCount();

        TaskModel webhookTask = taskMapperContext.createTaskModel();
        webhookTask.setTaskType(WAIT_FOR_WEBHOOK);
        webhookTask.setTaskDefName(WAIT_FOR_WEBHOOK);
        long epochMillis = System.currentTimeMillis();
        webhookTask.setStartTime(epochMillis);
        webhookTask.setEndTime(epochMillis);
        webhookTask.setInputData(taskInput);
        webhookTask.setRetryCount(retryCount);
        webhookTask.setStatus(TaskModel.Status.IN_PROGRESS);

        return List.of(webhookTask);
    }
}
