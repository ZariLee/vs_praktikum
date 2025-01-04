package com.vs.starnet.star;

import com.vs.starnet.star.filter.SecondPortFilter;
import com.vs.starnet.star.network.UdpHandler;
import com.vs.starnet.star.repository.SolRepository;
import com.vs.starnet.star.service.ApplicationState;
import com.vs.starnet.star.service.ComponentService;
import com.vs.starnet.star.service.StarService;
import com.vs.starnet.star.ui.CommandListener;
import jakarta.annotation.PostConstruct;
import org.apache.catalina.connector.Connector;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Random;

@SpringBootApplication
public class StarApplication {
    static final Logger LOGGER = LogManager.getRootLogger();

    @Autowired
    private ComponentService componentService;


    public static void main(String[] args) {
        // Ensure all necessary arguments are passed
        if (args.length < 3) {
            LOGGER.error("Usage: java -jar <app.jar> <serverPort> <galaxyPort> <groupId> <maxComponents>");
            System.exit(1);
        }

        // Set port for TCP (serverPort + groupId)
        int serverPort = Integer.parseInt(args[0]) + Integer.parseInt(args[2]);

        // Set port for Galaxy (galaxyPort)
        ApplicationState.setGalaxyPort(Integer.parseInt(args[0])+Integer.parseInt(args[1]));


        // Get server port from the command-line argument;
        System.setProperty("server.port", String.valueOf(serverPort));

        // Start Spring Boot application
        ApplicationContext context = SpringApplication.run(StarApplication.class, args);
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "StarPort: {}", serverPort);
        LOGGER.log(Level.getLevel("STAR_DEBUG"), "GalaxyPort: {}", ApplicationState.getGalaxyPort());

        // Set group ID
        ApplicationState.setGroupId(args[2]);
        // Set maximum number of components
        ApplicationState.setMaxComponents(Integer.parseInt(args[3]));
        try {
            ApplicationState.setIp(InetAddress.getByName(InetAddress.getLocalHost().getHostAddress()));
            LOGGER.log(Level.getLevel("STAR_DEBUG"), "IP Address: {}", ApplicationState.getIp());
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        // Set TCP and UDP port
        ApplicationState.setPort(serverPort);

        // Generate a random component UUID
        Random rand = new Random();
        ApplicationState.setComUuid(String.valueOf(rand.nextInt(9000) + 1000));


        ComponentService componentService = context.getBean(ComponentService.class);
        componentService.startComponent();
    }

    @Bean
    public ConfigurableServletWebServerFactory secondaryServer() {
        return new TomcatServletWebServerFactory() {
            @PostConstruct
            public void configureAdditionalPort() {
                this.addAdditionalTomcatConnectors(createConnectorForPort(ApplicationState.getGalaxyPort()));
            }

            private Connector createConnectorForPort(int port) {
                Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                connector.setPort(port);
                return connector;
            }
        };
    }

    @Bean
    public FilterRegistrationBean<SecondPortFilter> secondPortFilter() {
        FilterRegistrationBean<SecondPortFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new SecondPortFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1);
        return registrationBean;
    }

    @Bean
    public CommandLineRunner runCommandListener(CommandListener commandListener) {
        return args -> {
            // Start the CommandListener in a new thread
            Thread listenerThread = new Thread(commandListener);
            listenerThread.start();
        };
    }
}
