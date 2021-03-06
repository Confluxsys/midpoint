/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.model.impl.controller;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;

import com.evolveum.midpoint.model.impl.controller.ModelController;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * 
 * @author lazyman
 * 
 */
@ContextConfiguration(locations = { "classpath:ctx-model-test-no-repo.xml" })
public class ControllerTestResourceTest extends AbstractTestNGSpringContextTests {

	private static final Trace LOGGER = TraceManager.getTrace(ControllerTestResourceTest.class);
	@Autowired(required = true)
	private ModelController controller;
	@Autowired(required = true)
	private ProvisioningService provisioning;

	@BeforeMethod
	public void before() {
		Mockito.reset(provisioning);
	}

	@Test(expectedExceptions = IllegalArgumentException.class)
	public void testResourceNullOid() throws ObjectNotFoundException {
		controller.testResource(null, null);
	}

}
