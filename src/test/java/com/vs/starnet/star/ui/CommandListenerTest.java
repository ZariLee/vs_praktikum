package com.vs.starnet.star.ui;

import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.service.ApplicationState;
import com.vs.starnet.star.service.ComponentService;
import com.vs.starnet.star.service.GalaxyService;
import com.vs.starnet.star.service.StarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import static org.mockito.Mockito.*;

class CommandListenerTest {

    @Mock
    private ComponentService componentService;

    @Mock
    private StarService starService;

    @Mock
    private GalaxyService galaxyService;

    private CommandListener commandListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        commandListener = new CommandListener(componentService, starService, galaxyService);
    }

    @Test
    void testHandleExit_ComponentRole() {
        ApplicationState.setIsReady(true);
        ApplicationState.setCurrentRole(NodeRole.COMPONENT);

        commandListenerTestExit();

        verify(componentService).deregisterComponent();
        verify(starService, never()).deregisterComponents();
        verify(galaxyService, never()).unregisterLocalStar();
    }

    @Test
    void testHandleExit_SolRole() {
        ApplicationState.setIsReady(true);
        ApplicationState.setCurrentRole(NodeRole.SOL);

        commandListenerTestExit();

        verify(starService).deregisterComponents();
        verify(galaxyService).unregisterLocalStar();
        verify(componentService, never()).deregisterComponent();
    }

    // Auxiliary method to trigger the exit case (since handleExit() is private).
    private void commandListenerTestExit() {
        try {
            java.lang.reflect.Method handleExitMethod = CommandListener.class
                    .getDeclaredMethod("handleExit");
            handleExitMethod.setAccessible(true); // force access
            handleExitMethod.invoke(commandListener);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
