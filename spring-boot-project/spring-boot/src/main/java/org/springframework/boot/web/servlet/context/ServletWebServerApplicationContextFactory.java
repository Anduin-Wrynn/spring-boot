/*
 * Copyright 2012-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.web.servlet.context;

import org.springframework.aot.AotDetector;
import org.springframework.boot.ApplicationContextFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * {@link ApplicationContextFactory} registered in {@code spring.factories} to support
 * {@link AnnotationConfigServletWebServerApplicationContext} and
 * {@link ServletWebServerApplicationContext}.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 */
class ServletWebServerApplicationContextFactory implements ApplicationContextFactory {

	@Override
	public Class<? extends ConfigurableEnvironment> getEnvironmentType(WebApplicationType webApplicationType) {
		return (webApplicationType != WebApplicationType.SERVLET) ? null : ApplicationServletEnvironment.class;
	}

	@Override
	public ConfigurableEnvironment createEnvironment(WebApplicationType webApplicationType) {
		/*
		 * 注意：此处涉及java多态的构造过程
		 * StandardServletEnvironment继承StandardEnvironment，
		 * StandardEnvironment继承AbstractEnvironment，
		 * AbstractEnvironment实现ConfigurableEnvironment接口。
		 * 
		 * 在初始化StandardServletEnvironment时，会调用所有父类的构造方法。
		 * AbstractEnvironment的构造方法中会调用抽象方法：customizePropertySources
		 * 所以在子类的customizePropertySources方法会在初始化时执行。
		 *
		 * 因此：StandardServletEnvironment对象中会有以下几种env属性：
		 * 	1、servletConfigInitParams(必然)
		 * 	2、servletContextInitParams(必然)
		 * 	3、jndiProperties(可选)
		 * 	4、systemProperties(父类StandardEnvironment)
		 * 	5、systemEnvironment(父类StandardEnvironment)
		 */
		return (webApplicationType != WebApplicationType.SERVLET) ? null : new ApplicationServletEnvironment();
	}

	@Override
	public ConfigurableApplicationContext create(WebApplicationType webApplicationType) {
		return (webApplicationType != WebApplicationType.SERVLET) ? null : createContext();
	}

	private ConfigurableApplicationContext createContext() {
		if (!AotDetector.useGeneratedArtifacts()) {
			return new AnnotationConfigServletWebServerApplicationContext();
		}
		return new ServletWebServerApplicationContext();
	}

}
