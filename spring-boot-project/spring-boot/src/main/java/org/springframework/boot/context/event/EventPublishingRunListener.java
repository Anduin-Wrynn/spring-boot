/*
 * Copyright 2012-2023 the original author or authors.
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

package org.springframework.boot.context.event;

import java.time.Duration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ErrorHandler;

/**
 * {@link SpringApplicationRunListener} to publish {@link SpringApplicationEvent}s.
 * <p>
 * Uses an internal {@link ApplicationEventMulticaster} for the events that are fired
 * before the context is actually refreshed.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Artsiom Yudovin
 * @author Brian Clozel
 * @author Chris Bono
 */
class EventPublishingRunListener implements SpringApplicationRunListener, Ordered {

	private final SpringApplication application;

	private final String[] args;

	private final SimpleApplicationEventMulticaster initialMulticaster;

	EventPublishingRunListener(SpringApplication application, String[] args) {
		this.application = application;
		this.args = args;
		this.initialMulticaster = new SimpleApplicationEventMulticaster();
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public void starting(ConfigurableBootstrapContext bootstrapContext) {
		/**
		 * ApplicationStartingEvent事件有两个listener，分别为
		 * 1、org.springframework.boot.context.logging.LoggingApplicationListener
		 * 2、org.springframework.boot.liquibase.LiquibaseServiceLocatorApplicationListener
		 */
		multicastInitialEvent(new ApplicationStartingEvent(bootstrapContext, this.application, this.args));
	}

	@Override
	public void environmentPrepared(ConfigurableBootstrapContext bootstrapContext,
			ConfigurableEnvironment environment) {
		/**
		 * ApplicationEnvironmentPreparedEvent此事件有以下六个listener处理
		 * 1、org.springframework.boot.context.FileEncodingApplicationListener
		 * 2、org.springframework.boot.context.config.AnsiOutputApplicationListener
		 * 3、org.springframework.boot.context.config.DelegatingApplicationListener
		 * 4、org.springframework.boot.context.logging.ClasspathLoggingApplicationListener
		 * 5、org.springframework.boot.context.logging.LoggingApplicationListener
		 */
		multicastInitialEvent(
				new ApplicationEnvironmentPreparedEvent(bootstrapContext, this.application, this.args, environment));
	}

	@Override
	public void contextPrepared(ConfigurableApplicationContext context) {
		multicastInitialEvent(new ApplicationContextInitializedEvent(this.application, this.args, context));
	}

	@Override
	public void contextLoaded(ConfigurableApplicationContext context) {
		for (ApplicationListener<?> listener : this.application.getListeners()) {
			if (listener instanceof ApplicationContextAware contextAware) {
				contextAware.setApplicationContext(context);
			}
			context.addApplicationListener(listener);
		}
		multicastInitialEvent(new ApplicationPreparedEvent(this.application, this.args, context));
	}

	@Override
	public void started(ConfigurableApplicationContext context, Duration timeTaken) {
		context.publishEvent(new ApplicationStartedEvent(this.application, this.args, context, timeTaken));
		AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
	}

	@Override
	public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
		context.publishEvent(new ApplicationReadyEvent(this.application, this.args, context, timeTaken));
		AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
	}

	@Override
	public void failed(ConfigurableApplicationContext context, Throwable exception) {
		ApplicationFailedEvent event = new ApplicationFailedEvent(this.application, this.args, context, exception);
		if (context != null && context.isActive()) {
			// Listeners have been registered to the application context so we should
			// use it at this point if we can
			context.publishEvent(event);
		}
		else {
			// An inactive context may not have a multicaster so we use our multicaster to
			// call all the context's listeners instead
			if (context instanceof AbstractApplicationContext abstractApplicationContext) {
				for (ApplicationListener<?> listener : abstractApplicationContext.getApplicationListeners()) {
					this.initialMulticaster.addApplicationListener(listener);
				}
			}
			this.initialMulticaster.setErrorHandler(new LoggingErrorHandler());
			this.initialMulticaster.multicastEvent(event);
		}
	}

	private void multicastInitialEvent(ApplicationEvent event) {
		refreshApplicationListeners();
		this.initialMulticaster.multicastEvent(event);
	}

	
	private void refreshApplicationListeners() {
		/**
		 * application.getListeners()获取spring.factories文件中
		 * org.springframework.context.ApplicationListener的配置类
		 * 如下为所有listener：
		 * 1、类org.springframework.boot.ClearCachesApplicationListener
		 * 		对应事件：ContextRefreshedEvent
		 * 2、org.springframework.boot.builder.ParentContextCloserApplicationListener
		 * 		对应事件：ParentContextAvailableEvent
		 * 3、org.springframework.boot.cloud.CloudFoundryVcapEnvironmentPostProcessor
		 * 		对应事件：ApplicationPreparedEvent
		 * 5、org.springframework.boot.context.config.AnsiOutputApplicationListener
		 * 		对应事件：ApplicationEnvironmentPreparedEvent
		 * 6、org.springframework.boot.context.config.ConfigFileApplicationListener
		 * 		对应事件：ApplicationEnvironmentPreparedEvent
		 * 				ApplicationPreparedEvent
		 * 7、org.springframework.boot.context.config.DelegatingApplicationListener
		 * 		对应事件：ApplicationEnvironmentPreparedEvent
		 * 				ApplicationEvent
		 * 8、org.springframework.boot.context.logging.ClasspathLoggingApplicationListener
		 * 		对应事件：ApplicationEnvironmentPreparedEvent
		 * 				ApplicationFailedEvent
		 * 9、org.springframework.boot.context.logging.LoggingApplicationListener
		 * 		对应事件：ApplicationStartingEvent
		 * 				ApplicationEnvironmentPreparedEvent
		 * 				ApplicationPreparedEvent
		 * 				ContextClosedEvent
		 * 				ApplicationFailedEvent
		 * 10、org.springframework.boot.liquibase.LiquibaseServiceLocatorApplicationListener
		 * 		对应事件：ApplicationStartingEvent
		 * 
		 * 1、org.springframework.boot.context.FileEncodingApplicationListener
		 *		对应事件：ApplicationEnvironmentPreparedEvent
		 *  2、org.springframework.boot.context.config.AnsiOutputApplicationListener
		 *		对应事件：ApplicationEnvironmentPreparedEvent
		 *  3、org.springframework.boot.context.config.DelegatingApplicationListener
		 *		对应事件：ApplicationEnvironmentPreparedEvent
		 *  4、org.springframework.boot.context.logging.LoggingApplicationListener
		 * 
		 *	5、org.springframework.boot.ClearCachesApplicationListener
		 *		对应事件：ApplicationStartingEvent
		 *				ApplicationEnvironmentPreparedEvent
		 *				ApplicationPreparedEvent
		 *				ContextClosedEvent
		 *				ApplicationFailedEvent
		 *	6、org.springframework.boot.builder.ParentContextCloserApplicationListener
		 *		对应事件：ParentContextAvailableEvent
		 *	7、org.springframework.boot.env.EnvironmentPostProcessorApplicationListener
		 *		对应事件：ApplicationEnvironmentPreparedEvent
		 */
		this.application.getListeners().forEach(this.initialMulticaster::addApplicationListener);
	}

	private static class LoggingErrorHandler implements ErrorHandler {

		private static final Log logger = LogFactory.getLog(EventPublishingRunListener.class);

		@Override
		public void handleError(Throwable throwable) {
			logger.warn("Error calling ApplicationEventListener", throwable);
		}

	}

}
