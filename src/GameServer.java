import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private static final int PORT = 5000;
    // Introduce ~200ms latency
    private static final int LATENCY_MS = 200;

    // Game State
    private static final Map<Integer, PlayerHandler> players = new ConcurrentHashMap<>();
    private static int coinX = 300, coinY = 300;
    private static final int MAP_WIDTH = 600, MAP_HEIGHT = 400;
    private static final int PLAYER_SPEED = 5;
    private static final int PLAYER_SIZE = 30;
    private static final int COIN_SIZE = 15;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Server started on port " + PORT);

        // Start Game Loop
        new Thread(GameServer::gameLoop).start();

        int idCounter = 1;
        while (true) {
            Socket clientSocket = serverSocket.accept();
            PlayerHandler player = new PlayerHandler(clientSocket, idCounter++);
            players.put(player.id, player);
            new Thread(player).start();
            System.out.println("Player " + player.id + " connected.");
        }
    }

    private static void gameLoop() {
        while (true) {
            try {
                // 1. Update Physics (Server Authority)
                for (PlayerHandler p : players.values()) {
                    if (p.inputUp) p.y = Math.max(0, p.y - PLAYER_SPEED);
                    if (p.inputDown) p.y = Math.min(MAP_HEIGHT - PLAYER_SIZE, p.y + PLAYER_SPEED);
                    if (p.inputLeft) p.x = Math.max(0, p.x - PLAYER_SPEED);
                    if (p.inputRight) p.x = Math.min(MAP_WIDTH - PLAYER_SIZE, p.x + PLAYER_SPEED);

                    // 2. Collision Detection (Coin)
                    if (isColliding(p.x, p.y, coinX, coinY)) {
                        p.score++;
                        respawnCoin();
                    }
                }

                // 3. Broadcast State
                broadcastState();

                Thread.sleep(16); // ~60 FPS
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private static boolean isColliding(int px, int py, int cx, int cy) {
        return new java.awt.Rectangle(px, py, PLAYER_SIZE, PLAYER_SIZE)
                .intersects(new java.awt.Rectangle(cx, cy, COIN_SIZE, COIN_SIZE));
    }

    private static void respawnCoin() {
        coinX = (int) (Math.random() * (MAP_WIDTH - COIN_SIZE));
        coinY = (int) (Math.random() * (MAP_HEIGHT - COIN_SIZE));
    }

    private static void broadcastState() {
        // Format: START|CoinX,CoinY|ID,X,Y,Score|ID,X,Y,Score...|END
        StringBuilder sb = new StringBuilder("START|");
        sb.append(coinX).append(",").append(coinY).append("|");

        for (PlayerHandler p : players.values()) {
            sb.append(p.id).append(",").append(p.x).append(",")
                    .append(p.y).append(",").append(p.score).append("|");
        }
        sb.append("END");

        String msg = sb.toString();
        for (PlayerHandler p : players.values()) {
            p.sendMessage(msg);
        }
    }

    // Handles individual client connection
    static class PlayerHandler implements Runnable {
        Socket socket;
        PrintWriter out;
        BufferedReader in;
        int id;
        int x = 100, y = 100, score = 0;

        // Input Intents
        boolean inputUp, inputDown, inputLeft, inputRight;

        public PlayerHandler(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) { e.printStackTrace(); }
        }

        // Simulate Network Latency for sending
        public void sendMessage(String msg) {
            new Thread(() -> {
                try {
                    Thread.sleep(LATENCY_MS); // Artificial Lag
                    if (out != null) out.println(msg);
                } catch (Exception e) {}
            }).start();
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    // Introduce latency on receiving input as well
                    final String inputLine = line;
                    new Thread(() -> {
                        try {
                            Thread.sleep(LATENCY_MS);
                            processInput(inputLine);
                        } catch (InterruptedException e) {}
                    }).start();
                }
            } catch (IOException e) {
                players.remove(this.id);
            }
        }

        private void processInput(String cmd) {
            // Check for PING
            if (cmd.startsWith("PING:")) {
                // Send back PONG with the same timestamp
                // The sendMessage method will add the return latency automatically
                sendMessage(cmd.replace("PING", "PONG"));
                return;
            }

            // Client sends intent: "PRESS:UP" or "RELEASE:UP"
            String[] parts = cmd.split(":");
            if (parts.length < 2) return;
            boolean pressed = parts[0].equals("PRESS");
            switch (parts[1]) {
                case "UP": inputUp = pressed; break;
                case "DOWN": inputDown = pressed; break;
                case "LEFT": inputLeft = pressed; break;
                case "RIGHT": inputRight = pressed; break;
            }
        }
    }
}