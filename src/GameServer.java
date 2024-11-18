import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private static final int PORT = 12345;
    private static final int SHAPE_LIFETIME = 5000; // 5 seconds in milliseconds
    private static final int MAX_SCORE = 100;
    private static final int SCREEN_WIDTH = 800;
    private static final int SCREEN_HEIGHT = 600;

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private int score = 0;
        private String currentShape;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

                while (score < MAX_SCORE) {
                    // Generate and send shape
                    currentShape = generateShape();
                    out.println("SHAPE " + currentShape);
                    System.out.println("Sent shape to " + clientSocket.getRemoteSocketAddress() + ": " + currentShape);

                    // Wait for client response or timeout
                    clientSocket.setSoTimeout(SHAPE_LIFETIME);
                    try {
                        String response = in.readLine();
                        System.out.println(response);
                        if (response != null && response.startsWith("CLICK")) {
                            String[] parts = response.split(" ");
                            int r = Integer.parseInt(parts[1]);
                            int g = Integer.parseInt(parts[2]);
                            int b = Integer.parseInt(parts[3]);

                            // Check color and update score
                            if (r == 0 && g == 255 && b == 0) {
                                score += 10; // Green shape = +10 points
                            } else if (r == 255 && g == 0 && b == 0) {
                                score -= 10; // Red shape = -10 points
                            }

                            System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " clicked. Current score: " + score);
                        }
                    } catch (SocketTimeoutException e) {
                        System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " did not click in time.");
                    }

                    // Send updated score to client
                    out.println("SCORE " + score);
                }

                // Notify client game over
                out.println("GAME_OVER");
                System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " finished the game!");
            } catch (IOException e) {
                System.err.println("Connection error with client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Failed to close client socket: " + e.getMessage());
                }
            }
        }

        private String generateShape() {
            Random rand = new Random();
            int r = rand.nextBoolean() ? 255 : 0;
            int g = r == 0 ? 255 : 0;
            int b = 0;

            // Adjust coordinates to ensure shapes stay on the screen
            int radius = rand.nextInt(21) + 30; // Radius between 30 and 50
            int x = rand.nextInt(SCREEN_WIDTH - 2 * radius) + radius;
            int y = rand.nextInt(SCREEN_HEIGHT - 2 * radius) + radius;

            return r + " " + g + " " + b + " " + x + " " + y + " " + radius;
        }
    }

    public static void main(String[] args) {
        ExecutorService threadPool = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
