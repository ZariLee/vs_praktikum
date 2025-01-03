
# StarApp Docker Deployment Guide
## Build the Application To build the Docker image for the application, use the following command:
```
bash docker build -t starapp/latest .
```
## Create a Docker Network
To create a Docker network with a specific subnet, run:
```
docker network create --driver bridge --subnet=141.22.11.128/29 starnet
```
This will create a bridge network named `starnet` with the subnet `141.22.11.128/29`.

## Start Docker Containers
To run a container for the application, use the following command pattern:
```
docker run --net starnet \
  -p SolUdp<hostPort>:<ContainerPort>/udp \
  -p GalaxyUdp<hostPort>:<ContainerPort>/udp \
  SolPort[-p <hostPort>:<ContainerPort>] \
  GalaxyPort[-p <hostPort>:<ContainerPort>] \
  --ip <ip> \
  --rm -it starapp/latest <standard-port> <galaxy-id> <group-id> <max-components>
```

Example Usage:
Node 1:
```
docker run --net starnet \
  -p 8130:8130/udp -p 8200:8200/udp \
  -p 8130:8130 -p 8200:8200 \
  --ip 141.22.11.130 \
  --rm -it starapp/latest 8000 200 130 4
```

Node 2:
```
docker run --net starnet \
  -p 8230:8230/udp -p 8201:8200/udp \
  -p 8230:8230 -p 8201:8200 \
  --ip 141.22.11.131 \
  --rm -it starapp/latest 8000 200 230 4
```

**Key Difference**:  
For each new container, increment the port numbers to simulate a new node in the network. For example, Node 2 uses `230` for the `group-id` while keeping the `galaxy-id` constant at `200` to simulate multiple galaxies.

## Port Mapping Explained:

### UDP Ports:

-   `-p 8130:8130/udp`: Maps UDP traffic on port `8130` of the host machine to port `8130` of the container.
    -   **Usage**: Simulate UDP packets (e.g., using [Packet Sender](https://packetsender.com/)).
### TCP Ports:

-   `-p 8130:8130`: Maps TCP traffic on port `8130` of the host machine to port `8130` of the container.
    -   **Usage**: Simulate HTTP API requests (e.g., using [Postman](https://www.postman.com/) or [Bruno](https://github.com/usebruno/bruno)).

## Docker Options Explained:

-   **`--ip 141.22.11.130`**: Assigns a static IP address (`141.22.11.130`) to the container within the `starnet` Docker network.
-   **`--rm`**: Automatically removes the container when it stops.
-   **`-it`**:
    -   `-i`: Keeps the container’s standard input open, allowing interactive commands.
    -   `-t`: Allocates a pseudo-TTY, enabling terminal-like input/output.
    -   **Usage**: Used for sending commands to the container (e.g., `EXIT`, `CRASH`).

## Application Arguments:

When running the application in the container, the following arguments must be passed:

1.  **`<standard-port>`**: Base port for the application.
2.  **`<galaxy-id>`**: Galaxy port where the SOL discovery service will listen on.
3.  **`<group-id>`**: Identifier for the container's group.
4.  **`<max-components>`**: Maximum number of components for the node.
