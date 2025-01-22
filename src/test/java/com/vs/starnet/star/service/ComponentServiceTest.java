package com.vs.starnet.star.service;

import com.vs.starnet.star.network.UdpHandler;
import com.vs.starnet.star.repository.SolRepository;
import com.vs.starnet.star.model.Sol;
import com.vs.starnet.star.constants.NodeRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetAddress;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComponentServiceTest {

    @Mock
    private SolRepository solRepository;
    private ComponentService componentService;

    @BeforeEach
    void setUp() throws Exception {
        componentService = new ComponentService(solRepository);

        ApplicationState.setCurrentRole(NodeRole.COMPONENT);
        // IP + Port so getIp().getHostAddress() is not null
        ApplicationState.setIp(InetAddress.getByName("127.0.0.1"));
        ApplicationState.setPort(8080);
        ApplicationState.setStarUuid("test-star-uuid");
        ApplicationState.setComUuid("test-com-uuid");

        // Mock UdpHandler:
        UdpHandler udpHandlerMock = mock(UdpHandler.class);
        ReflectionTestUtils.setField(componentService, "udpHandler", udpHandlerMock);
    }


    @Test
    void testStartComponentWhenNoSolDiscovered() throws Exception {
        // Since we use startComponent() in various places
        // UdpHandler.sendBroadcast(...) and UdpHandler.isSolDiscovered() have,
        // let's mock the static methods via MockedStatic:
        try (MockedStatic<UdpHandler> udpHandlerStatic = Mockito.mockStatic(UdpHandler.class)) {
            // sendBroadcast() should simply do nothing:
            udpHandlerStatic
                    .when(() -> UdpHandler.sendBroadcast(anyString(), anyInt()))
                    .thenAnswer(invocation -> null);

            // isSolDiscovered() should return false in all calls
            // => Then the component goes into "promoteToSol()"
            udpHandlerStatic
                    .when(UdpHandler::isSolDiscovered)
                    .thenReturn(false);

            // Now we execute the actual test step
            componentService.startComponent();
        }

        // Since no SOL was found, the component should
        // called promoteToSol(). In the code there is
        // solRepository.save(...) executed.
        verify(solRepository, times(1))
                .save(anyString(), any(Sol.class));
    }

    // todo deregisterComponent() testen
}
