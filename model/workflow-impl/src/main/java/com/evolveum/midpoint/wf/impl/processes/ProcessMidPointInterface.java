/*
 * Copyright (c) 2010-2014 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.wf.impl.processes;

import com.evolveum.midpoint.wf.impl.messages.ProcessEvent;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns.model.workflow.process_instance_state_3.ProcessSpecificState;

import java.util.List;
import java.util.Map;

/**
 * @author mederly
 */
public interface ProcessMidPointInterface {

    String getAnswer(Map<String, Object> variables);

    String getState(Map<String, Object> variables);

    ProcessSpecificState externalizeProcessInstanceState(Map<String, Object> variables);

    String getBeanName();

    List<ObjectReferenceType> prepareApprovedBy(ProcessEvent event);
}
