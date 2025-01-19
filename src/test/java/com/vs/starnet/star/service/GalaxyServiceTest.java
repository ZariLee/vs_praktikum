package com.vs.starnet.star.service;

import com.vs.starnet.star.constants.NodeRole;
import com.vs.starnet.star.model.Sol;
import com.vs.starnet.star.repository.SolRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GalaxyServiceTest {

    @Mock
    private SolRepository solRepository;

    @InjectMocks
    private GalaxyService galaxyService;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ApplicationState.setCurrentRole(NodeRole.SOL);
        ApplicationState.setIp(InetAddress.getLoopbackAddress()); // Set to localhost for testing
        ApplicationState.setStarUuid("test-star-uuid");
        ApplicationState.setComUuid("test-com-uuid");
        ApplicationState.setPort(8080);
        ApplicationState.setGalaxyPort(9090);
    }


    @Test
    void testRegisterStar_Success() {
        Sol sol = Sol.builder()
                .solStarUuid("test-star-uuid")
                .solUuid("test-sol-uuid")
                .comIp("141.22.11.130")
                .comPort(8130)
                .noCom(1)
                .status("200")
                .build();

        // Simulate that the star does not exist
        when(solRepository.existsById(sol.getSolStarUuid())).thenReturn(false);

        // method call
        ResponseEntity<Sol> response = galaxyService.registerStar(sol);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(solRepository, times(1)).save(sol.getSolStarUuid(), sol);
    }

    @Test
    void testRegisterStar_Conflict() {
        Sol sol = Sol.builder().solStarUuid("test-star-uuid").build();

        // Simulate that the star already exists
        when(solRepository.existsById(sol.getSolStarUuid())).thenReturn(true);

        ResponseEntity<Sol> response = galaxyService.registerStar(sol);

        assertEquals(409, response.getStatusCode().value());
        verify(solRepository, never()).save(anyString(), any());
    }

    @Test
    void testUpdateStar_Success() {
        // Role is SOL
        ApplicationState.setCurrentRole(NodeRole.SOL);

        Sol sol = Sol.builder()
                .solStarUuid("test-star-uuid")
                .comIp("127.0.0.1")
                .comPort(8130)
                .status("active")
                .build();

        // Simulate that the star already exists
        when(solRepository.existsById(sol.getSolStarUuid())).thenReturn(true);

        ResponseEntity<Sol> response = galaxyService.updateStar("test-star-uuid", sol);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        verify(solRepository, times(1)).save(sol.getSolStarUuid(), sol);
    }

    @Test
    void testUpdateStar_NotSolRole() {
        // Role is not SOL
        ApplicationState.setCurrentRole(NodeRole.COMPONENT);

        Sol sol = Sol.builder().solStarUuid("test-star-uuid").build();

        ResponseEntity<Sol> response = galaxyService.updateStar("test-star-uuid", sol);

        assertEquals(503, response.getStatusCode().value());
        verify(solRepository, never()).save(anyString(), any());
    }

    @Test
    void testUpdateStar_EmptyStarUuid() {
        // Role is SOL
        ApplicationState.setCurrentRole(NodeRole.SOL);

        Sol sol = Sol.builder().solStarUuid("test-star-uuid").build();

        ResponseEntity<Sol> response = galaxyService.updateStar(null, sol);

        assertEquals(400, response.getStatusCode().value());
        verify(solRepository, never()).save(anyString(), any());

        ResponseEntity<Sol> response2 = galaxyService.updateStar("", sol);

        assertEquals(400, response2.getStatusCode().value());
        verify(solRepository, never()).save(anyString(), any());
    }

    @Test
    void testUpdateStar_StarNotFound() {
        // Rolle ist SOL
        ApplicationState.setCurrentRole(NodeRole.SOL);

        Sol sol = Sol.builder().solStarUuid("test-star-uuid").build();

        // Simulate that the star does not exist
        when(solRepository.existsById(sol.getSolStarUuid())).thenReturn(false);

        ResponseEntity<Sol> response = galaxyService.updateStar("test-star-uuid", sol);

        assertEquals(409, response.getStatusCode().value());
        verify(solRepository, never()).save(anyString(), any());
    }


    @Test
    void testGetStarDetail_Success() {
        Sol sol = Sol.builder().solStarUuid("test-star-uuid").build();

        // Simulate that the star already exists
        when(solRepository.existsById("test-star-uuid")).thenReturn(true);
        // returns the previously created Sol object, which means that the details of the star can be successfully retrieved
        when(solRepository.findById("test-star-uuid")).thenReturn(sol);

        ResponseEntity<Sol> response = galaxyService.getStarDetail("test-star-uuid");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(sol, response.getBody());
    }

    @Test
    void testGetStarDetail_NotFound() {
        // Simulate that the star does not exist
        when(solRepository.existsById("test-star-uuid")).thenReturn(false);

        ResponseEntity<Sol> response = galaxyService.getStarDetail("test-star-uuid");

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void testGetStarDetails_Success() {
        Sol sol1 = Sol.builder().solStarUuid("star1").build();
        Sol sol2 = Sol.builder().solStarUuid("star2").build();

        Map<String, Sol> mockStars = new HashMap<>();
        mockStars.put("star1", sol1);
        mockStars.put("star2", sol2);

        // define mock behavior
        when(solRepository.findAll()).thenReturn(mockStars);

        ResponseEntity<Map<String, Object>> response = galaxyService.getStarDetails();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        // access to the "stars" list from the map
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> starsList = (List<Map<String, Object>>) response.getBody().get("stars");

        assertEquals(2, starsList.size());

        // review the contents of the list
        Map<String, Object> star1Details = starsList.get(0);
        Map<String, Object> star2Details = starsList.get(1);

        assertEquals("star1", star1Details.get("star"));
        assertEquals("star2", star2Details.get("star"));
    }

    @Test
    void testDeregisterStar_Success() {
        Sol sol = Sol.builder()
                .solStarUuid("test-star-uuid")
                .comIp("127.0.0.1")
                .status("200")
                .build();

        // Create a mock for the HttpServletRequest object that simulates the HTTP request.
        HttpServletRequest request = mock(HttpServletRequest.class);
        // The getRemoteAddr() method is configured to return "127.0.0.1" to simulate the requester's IP address.
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        // Simulate that the star already exists
        when(solRepository.existsById("test-star-uuid")).thenReturn(true);
        // returns the previously created Sol object
        when(solRepository.findById("test-star-uuid")).thenReturn(sol);

        ResponseEntity<String> response = galaxyService.deregisterStar("test-star-uuid", request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Star deregistered: test-star-uuid", response.getBody());
        verify(solRepository, times(1)).delete("test-star-uuid");
    }

    @Test
    void testDeregisterStar_Unauthorized() {
        Sol sol = Sol.builder()
                .solStarUuid("test-star-uuid")
                .comIp("127.0.0.1")
                .status("200")
                .build();

        // Create a mock for the HttpServletRequest object that simulates the HTTP request.
        HttpServletRequest request = mock(HttpServletRequest.class);
        // The getRemoteAddr() method is configured to return "192.168.0.1", a different IP address than the one associated with the star.
        when(request.getRemoteAddr()).thenReturn("192.168.0.1");

        // Simulate that the star already exists
        when(solRepository.existsById("test-star-uuid")).thenReturn(true);
        // returns the previously created Sol object
        when(solRepository.findById("test-star-uuid")).thenReturn(sol);

        ResponseEntity<String> response = galaxyService.deregisterStar("test-star-uuid", request);

        assertEquals(401, response.getStatusCode().value());
        verify(solRepository, never()).delete(anyString());
    }

    // todo unregisterLocalStar() testen
}
