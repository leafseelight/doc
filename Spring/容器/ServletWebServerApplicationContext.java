package org.springframework.boot.web.servlet.context;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletContextInitializerBeans;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.ApplicationContextException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.DefaultResourceLoader.ClassPathContextResource;
import org.springframework.util.StringUtils;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.context.support.ServletContextResource;
import org.springframework.web.context.support.ServletContextScope;
import org.springframework.web.context.support.WebApplicationContextUtils;

public class ServletWebServerApplicationContext extends GenericWebApplicationContext implements ConfigurableWebServerApplicationContext {
    private static final Log logger = LogFactory.getLog(ServletWebServerApplicationContext.class);
    public static final String DISPATCHER_SERVLET_NAME = "dispatcherServlet";		// dispatcher的名称
    private volatile WebServer webServer;											// web 服务器
    private ServletConfig servletConfig;											// servlet配置
    private String serverNamespace;													// 命名空间

    public ServletWebServerApplicationContext() {
    }

    public ServletWebServerApplicationContext(DefaultListableBeanFactory beanFactory) {
        super(beanFactory);
    }

    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        beanFactory.addBeanPostProcessor(new WebApplicationContextServletContextAwareProcessor(this));
        beanFactory.ignoreDependencyInterface(ServletContextAware.class);
        this.registerWebApplicationScopes();
    }

    public final void refresh() throws BeansException, IllegalStateException {
        try {
            super.refresh();
        } catch (RuntimeException var2) {
            this.stopAndReleaseWebServer();
            throw var2;
        }
    }

    protected void onRefresh() {
        super.onRefresh();

        try {
			// 创建webserver
            this.createWebServer();
        } catch (Throwable var2) {
            throw new ApplicationContextException("Unable to start web server", var2);
        }
    }

    protected void finishRefresh() {
        super.finishRefresh();
		// 启动webserver
        WebServer webServer = this.startWebServer();
        if (webServer != null) {
            this.publishEvent(new ServletWebServerInitializedEvent(webServer, this));
        }

    }

    protected void onClose() {
        super.onClose();
		// 停止webserver
        this.stopAndReleaseWebServer();
    }

	// 使用ServletWebServerFactory创建WebServer
    private void createWebServer() {
        WebServer webServer = this.webServer;
        ServletContext servletContext = this.getServletContext();
        if (webServer == null && servletContext == null) {
            ServletWebServerFactory factory = this.getWebServerFactory();
            this.webServer = factory.getWebServer(new ServletContextInitializer[]{this.getSelfInitializer()});
        } else if (servletContext != null) {
            try {
                this.getSelfInitializer().onStartup(servletContext);
            } catch (ServletException var4) {
                throw new ApplicationContextException("Cannot initialize servlet context", var4);
            }
        }

        this.initPropertySources();
    }

    protected ServletWebServerFactory getWebServerFactory() {
        String[] beanNames = this.getBeanFactory().getBeanNamesForType(ServletWebServerFactory.class);
        if (beanNames.length == 0) {
            throw new ApplicationContextException("Unable to start ServletWebServerApplicationContext due to missing ServletWebServerFactory bean.");
        } else if (beanNames.length > 1) {
            throw new ApplicationContextException("Unable to start ServletWebServerApplicationContext due to multiple ServletWebServerFactory beans : " + StringUtils.arrayToCommaDelimitedString(beanNames));
        } else {
            return (ServletWebServerFactory)this.getBeanFactory().getBean(beanNames[0], ServletWebServerFactory.class);
        }
    }

    private ServletContextInitializer getSelfInitializer() {
        return this::selfInitialize;
    }

    private void selfInitialize(ServletContext servletContext) throws ServletException {
        this.prepareWebApplicationContext(servletContext);
        this.registerApplicationScope(servletContext);
        WebApplicationContextUtils.registerEnvironmentBeans(this.getBeanFactory(), servletContext);
        Iterator var2 = this.getServletContextInitializerBeans().iterator();

        while(var2.hasNext()) {
            ServletContextInitializer beans = (ServletContextInitializer)var2.next();
            beans.onStartup(servletContext);
        }

    }

    private void registerApplicationScope(ServletContext servletContext) {
        ServletContextScope appScope = new ServletContextScope(servletContext);
        this.getBeanFactory().registerScope("application", appScope);
        servletContext.setAttribute(ServletContextScope.class.getName(), appScope);
    }

    private void registerWebApplicationScopes() {
        ServletWebServerApplicationContext.ExistingWebApplicationScopes existingScopes = new ServletWebServerApplicationContext.ExistingWebApplicationScopes(this.getBeanFactory());
        WebApplicationContextUtils.registerWebApplicationScopes(this.getBeanFactory());
        existingScopes.restore();
    }

    protected Collection<ServletContextInitializer> getServletContextInitializerBeans() {
        return new ServletContextInitializerBeans(this.getBeanFactory(), new Class[0]);
    }

    protected void prepareWebApplicationContext(ServletContext servletContext) {
        Object rootContext = servletContext.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
        if (rootContext != null) {
            if (rootContext == this) {
                throw new IllegalStateException("Cannot initialize context because there is already a root application context present - check whether you have multiple ServletContextInitializers!");
            }
        } else {
            Log logger = LogFactory.getLog(ContextLoader.class);
            servletContext.log("Initializing Spring embedded WebApplicationContext");

            try {
                servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, this);
                if (logger.isDebugEnabled()) {
                    logger.debug("Published root WebApplicationContext as ServletContext attribute with name [" + WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE + "]");
                }

                this.setServletContext(servletContext);
                if (logger.isInfoEnabled()) {
                    long elapsedTime = System.currentTimeMillis() - this.getStartupDate();
                    logger.info("Root WebApplicationContext: initialization completed in " + elapsedTime + " ms");
                }

            } catch (Error | RuntimeException var6) {
                logger.error("Context initialization failed", var6);
                servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, var6);
                throw var6;
            }
        }
    }

    private WebServer startWebServer() {
        WebServer webServer = this.webServer;
        if (webServer != null) {
            webServer.start();
        }

        return webServer;
    }

    private void stopAndReleaseWebServer() {
        WebServer webServer = this.webServer;
        if (webServer != null) {
            try {
                webServer.stop();
                this.webServer = null;
            } catch (Exception var3) {
                throw new IllegalStateException(var3);
            }
        }

    }

    protected Resource getResourceByPath(String path) {
        return (Resource)(this.getServletContext() == null ? new ClassPathContextResource(path, this.getClassLoader()) : new ServletContextResource(this.getServletContext(), path));
    }

    public String getServerNamespace() {
        return this.serverNamespace;
    }

    public void setServerNamespace(String serverNamespace) {
        this.serverNamespace = serverNamespace;
    }

    public void setServletConfig(ServletConfig servletConfig) {
        this.servletConfig = servletConfig;
    }

    public ServletConfig getServletConfig() {
        return this.servletConfig;
    }

    public WebServer getWebServer() {
        return this.webServer;
    }

    public static class ExistingWebApplicationScopes {
        private static final Set<String> SCOPES;
        private final ConfigurableListableBeanFactory beanFactory;
        private final Map<String, Scope> scopes = new HashMap();

        public ExistingWebApplicationScopes(ConfigurableListableBeanFactory beanFactory) {
            this.beanFactory = beanFactory;
            Iterator var2 = SCOPES.iterator();

            while(var2.hasNext()) {
                String scopeName = (String)var2.next();
                Scope scope = beanFactory.getRegisteredScope(scopeName);
                if (scope != null) {
                    this.scopes.put(scopeName, scope);
                }
            }

        }

        public void restore() {
            this.scopes.forEach((key, value) -> {
                if (ServletWebServerApplicationContext.logger.isInfoEnabled()) {
                    ServletWebServerApplicationContext.logger.info("Restoring user defined scope " + key);
                }

                this.beanFactory.registerScope(key, value);
            });
        }

        static {
            Set<String> scopes = new LinkedHashSet();
            scopes.add("request");
            scopes.add("session");
            SCOPES = Collections.unmodifiableSet(scopes);
        }
    }
}
