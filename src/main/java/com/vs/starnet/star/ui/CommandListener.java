package com.vs.starnet.star.ui;

import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.service.ApplicationState;
import com.vs.starnet.star.service.ComponentService;
import com.vs.starnet.star.service.GalaxyService;
import com.vs.starnet.star.service.StarService;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Scanner;


/**
 */
@Component
public class CommandListener implements Runnable {
    static final Logger LOGGER = LogManager.getRootLogger();
    @Autowired
    private final ComponentService componentService;
    @Autowired
    private final StarService starService;
    @Autowired
    private final GalaxyService galaxyService;

    public CommandListener(ComponentService componentService, StarService starService, GalaxyService galaxyService) {
        this.componentService = componentService;
        this.starService = starService;
        this.galaxyService = galaxyService;
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String command = scanner.nextLine().trim().toUpperCase();
            switch (command) {
                case "CRASH":
                    handleCrash();
                    break;
                case "EXIT":
                    if (ApplicationState.getIsReady()) {
                        handleExit();
                    } else {
                        LOGGER.error("Cannot exit the application. The component is not ready.");
                    }
                    break;
                default:
                    LOGGER.log(Level.getLevel("STAR_INFO"), "Unknown command. Available commands: CRASH, EXIT");
            }
        }
    }

    private void handleCrash() {
        LOGGER.error("Crashing the application...");
        System.exit(1);
    }

    private void handleExit() {
        LOGGER.log(Level.getLevel("STAR_INFO"), "Exiting the application...");
        if (ApplicationState.getCurrentRole() == NodeRole.COMPONENT) {
            componentService.deregisterComponent();
        } else if (ApplicationState.getCurrentRole() == NodeRole.SOL) {
            galaxyService.unregisterLocalStar();
            starService.deregisterComponents();
        }
    }
}

