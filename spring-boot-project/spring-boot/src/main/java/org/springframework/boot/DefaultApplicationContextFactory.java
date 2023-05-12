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

package org.springframework.boot;

import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.springframework.aot.AotDetector;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * Default {@link ApplicationContextFactory} implementation that will create an
 * appropriate context for the {@link WebApplicationType}.
 *
 * @author Phillip Webb
 */
class DefaultApplicationContextFactory implements ApplicationContextFactory {

	@Override
	public Class<? extends ConfigurableEnvironment> getEnvironmentType(WebApplicationType webApplicationType) {
		return getFromSpringFactories(webApplicationType, ApplicationContextFactory::getEnvironmentType, null);
	}

	@Override
	public ConfigurableEnvironment createEnvironment(WebApplicationType webApplicationType) {
		/*
		 * 根据应用类型，调用对应实现类的createEnvironment方法
		 */
		return getFromSpringFactories(webApplicationType, ApplicationContextFactory::createEnvironment, null);
	}

	@Override
	public ConfigurableApplicationContext create(WebApplicationType webApplicationType) {
		try {
			return getFromSpringFactories(webApplicationType, ApplicationContextFactory::create,
					this::createDefaultApplicationContext);
		}
		catch (Exception ex) {
			throw new IllegalStateException("Unable create a default ApplicationContext instance, "
					+ "you may need a custom ApplicationContextFactory", ex);
		}
	}

	private ConfigurableApplicationContext createDefaultApplicationContext() {
		if (!AotDetector.useGeneratedArtifacts()) {
			return new AnnotationConfigApplicationContext();
		}
		return new GenericApplicationContext();
	}

	private <T> T getFromSpringFactories(WebApplicationType webApplicationType,
			BiFunction<ApplicationContextFactory, WebApplicationType, T> action, Supplier<T> defaultResult) {
		/*
		 * 获取spring.factories文件中org.springframework.boot.ApplicationContextFactory的所有实现
		 * 1、org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContextFactory
		 * 		
		 * 2、org.springframework.boot.web.servlet.context.ServletWebServerApplicationContextFactory
		 */
		for (ApplicationContextFactory candidate : SpringFactoriesLoader.loadFactories(ApplicationContextFactory.class,
				getClass().getClassLoader())) {
			/*
			 * 根据应用启动类型，调用ReactiveWebServerApplicationContextFactory或者
			 * ServletWebServerApplicationContextFactory对应的具体action方法，
			 * 由方法的调用方传递推来
			 * 1、在createEnvironment时，传递的action为createEnvironment，则会调用factory的createEnvironment方法
			 * 2、在create是，传递的action为create，则会调用factory的create方法
			 * 3、在getEnvironmentType时，传递的action为getEnvironmentType，则会调用getEnvironmentType方法
			 */
			T result = action.apply(candidate, webApplicationType);
			if (result != null) {
				return result;
			}
		}
		return (defaultResult != null) ? defaultResult.get() : null;
	}

}
