import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * GameServer
 * A multi-client server for a shape-clicking game. The game ends when one client achieves the maximum score,
 * and all other clients are notified of the "GAME_OVER" event.
 */
public class GameServer {
    // Constants
    private static final int PORT = 12345; // Port on which the server listens
    private static final int SHAPE_LIFETIME = 2000; // Shape visibility duration in milliseconds
    private static final int MAX_SCORE = 50; // Score required to win the game
    private static final int SCREEN_WIDTH = 800; // Width of the screen
    private static final int SCREEN_HEIGHT = 600; // Height of the screen

    /**
     * ClientHandler
     * Manages the connection with a single client.
     */
    private static class ClientHandler implements Runnable {
        private Socket clientSocket; // Client's socket connection
        private PrintWriter out; // Output stream to send data to the client
        private BufferedReader in; // Input stream to receive data from the client
        private int score = 0; // Client's current score

        // Shared variables among all client handlers
        private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
        private static volatile boolean gameOver = false; // Indicates whether the game is over

        /**
         * Constructor
         * @param socket Client's socket connection
         */
        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        /**
         * Notify all connected clients with a given message.
         * @param message The message to send to all clients
         */
        private static void notifyAllClients(String message) {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    client.sendMessage(message);
                }
            }
        }

        /**
         * Main logic for handling a client's interaction with the server.
         */
        @Override
        public void run() {
            try {
                // Initialize communication streams
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

                // Game loop for this client
                while (!gameOver && score < MAX_SCORE) {
                    // Generate and send a new shape to the client
                    String currentShape = generateShape();
                    out.println("SHAPE " + currentShape);
                    System.out.println("Sent shape to " + clientSocket.getRemoteSocketAddress() + ": " + currentShape);

                    // Wait for client's response or timeout
                    clientSocket.setSoTimeout(SHAPE_LIFETIME); // Set response timeout
                    try {
                        String response = in.readLine();
                        if (response == null) {
                            System.out.println("Client disconnected unexpectedly: " + clientSocket.getRemoteSocketAddress());
                            break;
                        }

                        System.out.println("Received response: " + response);
                        handleClientResponse(response);
                    } catch (SocketTimeoutException e) {
                        System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " did not click in time.");
                    }

                    // Check if the client has won the game
                    if (!gameOver && score >= MAX_SCORE) {
                        gameOver = true; // End the game
                        notifyAllClients("GAME_OVER"); // Notify all clients
                        System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " won the game!");
                    }
                }

                // Notify this client of the game over status
                out.println("GAME_OVER");
                System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " finished the game!");

            } catch (IOException e) {
                System.err.println("Error with client " + clientSocket.getRemoteSocketAddress() + ": " + e.getMessage());
            } finally {
                // Cleanup resources for this client
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Failed to close client socket: " + e.getMessage());
                }
            }
        }

        /**
         * Handle a response from the client.
         * @param response The response string from the client
         */
        private void handleClientResponse(String response) {
            try {
                String[] parts = response.split(" ");
                if (parts.length != 4 || !parts[0].equals("CLICK")) {
                    System.out.println("Invalid response format: " + response);
                    return;
                }

                // Parse RGB color values
                int r = Integer.parseInt(parts[1]);
                int g = Integer.parseInt(parts[2]);
                int b = Integer.parseInt(parts[3]);

                // Update score based on color
                if (r == 0 && g == 255 && b == 0) {
                    score += 10; // Green shape = +10 points
                } else if (r == 255 && g == 0 && b == 0) {
                    score -= 10; // Red shape = -10 points
                }
                out.println("SCORE "+ score);
                System.out.println("Updated score for client " + clientSocket.getRemoteSocketAddress() + ": " + score);
            } catch (NumberFormatException e) {
                System.out.println("Error parsing client response: " + response);
            }
        }

        /**
         * Generate a random shape's attributes (color and position).
         * @return The string representation of the shape's attributes
         */
        private String generateShape() {
            Random rand = new Random();
            int r = rand.nextBoolean() ? 255 : 0; // Randomly red or green
            int g = (r == 255) ? 0 : 255; // Opposite of red for green
            int b = 0;

            // Ensure the shape stays fully visible on the screen
            int radius = rand.nextInt(21) + 30; // Radius between 30 and 50
            int x = rand.nextInt(SCREEN_WIDTH - 2 * radius) + radius;
            int y = rand.nextInt(SCREEN_HEIGHT - 2 * radius) + radius;

            return r + " " + g + " " + b + " " + x + " " + y + " " + radius;
        }

        /**
         * Send a message to this client.
         * @param message The message to send
         */
        public void sendMessage(String message) {
            out.println(message);
        }
    }

    /**
     * Main method. Starts the server and listens for incoming client connections.
     */
    public static void main(String[] args) {
        // Thread pool for handling multiple clients
        ExecutorService threadPool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server running on port " + PORT);

            // Accept clients and start a new thread for each
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                ClientHandler.clients.add(handler); // Add to the shared client list
                threadPool.execute(handler); // Run the handler in a separate thread
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
