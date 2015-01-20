package com.aol.micro.server.servers;

import java.io.IOException;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContextListener;

import lombok.Getter;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.accesslog.AccessLogBuilder;
import org.glassfish.grizzly.servlet.FilterRegistration;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.ContextLoaderListener;

import com.aol.micro.server.ErrorCode;
import com.aol.micro.server.rest.RestContextListener;
import com.aol.micro.server.servers.model.FilterData;
import com.aol.micro.server.servers.model.ServerData;

public class ServerApplication {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Getter
	private final ServerData serverData;

	public ServerApplication(ServerData serverData) {
		this.serverData = serverData;
	}

	public void run() {

		WebappContext webappContext = new WebappContext("WebappContext", "");

		addServlet(webappContext);

		addFilters(webappContext);

		addListeners(webappContext);

		HttpServer httpServer = HttpServer.createSimpleServer(null, "0.0.0.0", serverData.getPort());

		addAccessLog(httpServer);

		startServer(webappContext, httpServer);
	}

	private void startServer(WebappContext webappContext, HttpServer httpServer) {
		webappContext.deploy(httpServer);
		try {
			logger.info("Starting application {} on port {}", serverData.getModule().getContext(), serverData.getPort());
			logger.info("Browse to http://localhost:{}/{}/application.wadl", serverData.getPort(), serverData.getModule().getContext());
			httpServer.start();
			while (true) {
				Thread.sleep(2000L);
			}
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException(e);
		} finally {
			httpServer.stop();
		}
	}

	private void addAccessLog(HttpServer httpServer) {
		try {
			String accessLogLocation = serverData.getRootContext().getBean(AccessLogLocationBean.class).getAccessLogLocation();
			final AccessLogBuilder builder = new AccessLogBuilder(accessLogLocation + serverData.getModule().getContext() + "-access.log");
			builder.rotatedDaily();
			builder.rotationPattern("yyyy-MM-dd");
			builder.instrument(httpServer.getServerConfiguration());
		} catch (Exception e) {
			logger.error(ErrorCode.SERVER_STARTUP_FAILED_TO_CREATE_ACCESS_LOG.toString() + ": " + e.getMessage(), e);
		}

	}

	private void addServlet(WebappContext webappContext) {
		ServletContainer container = new ServletContainer();
		ServletRegistration servletRegistration = webappContext.addServlet("Jersey Spring Web Application", container);
		servletRegistration.setInitParameter("javax.ws.rs.Application", "com.aol.micro.server.rest.RestApplication");
		servletRegistration.setInitParameter("jersey.config.server.provider.packages", "com.aol.micro.server.rest.providers");
		servletRegistration.setLoadOnStartup(1);
		servletRegistration.addMapping(serverData.getBaseUrlPattern());
	}

	private void addFilters(WebappContext webappContext) {
		for (FilterData filterData : serverData.getFilterDataList()) {
			FilterRegistration filterReg = webappContext.addFilter(filterData.getFilterName(), filterData.getFilter());
			filterReg.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), filterData.getMapping());
		}
	}

	private void addListeners(WebappContext webappContext) {
		webappContext.addListener(new ContextLoaderListener(serverData.getRootContext()));
		webappContext.addListener(new RestContextListener(serverData));
		serverData.getRootContext().getBeansOfType(ServletContextListener.class).values().forEach(listener -> webappContext.addListener(listener));
	}

}