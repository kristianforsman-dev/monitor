package se.apendo.flowmon.bootstrap;


import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import se.apendo.flowmon.core.ConfigLoader;
import se.apendo.flowmon.core.PollerService;

/**
 * Startar PollerService vid deploy och stoppar vid undeploy.
 */
public class AppBootstrapListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ConfigLoader cfg = new ConfigLoader();

            // Lägg flows.json i: FlowMonitorWeb/WebContent/WEB-INF/classes/flows.json
            // (Eclipse kopierar ofta detta till WEB-INF/classes vid build)
            cfg.loadFromClasspath("/flows.json");

            PollerService poller = new PollerService(cfg);
            sce.getServletContext().setAttribute(PollerService.CTX_KEY, poller);
            poller.start();
        } catch (Exception e) {
            throw new RuntimeException("AppBootstrapListener init failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        Object o = sce.getServletContext().getAttribute(PollerService.CTX_KEY);
        if (o instanceof PollerService) {
            try { ((PollerService) o).stop(); } catch (Exception ignore) {}
        }
    }
}



