import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameClient extends JPanel {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int PORT = 5000;

    // Client-side representation of an entity
    static class Entity {
        int targetX, targetY; // Where the server says we should be
        double currentX, currentY; // Where we actually draw (Interpolated)
        int score;
    }

    private final Map<Integer, Entity> entities = new ConcurrentHashMap<>();
    private int coinX, coinY;
    private PrintWriter out;

    // Latency Monitoring
    private long currentPing = 0;

    public GameClient() {
        setPreferredSize(new Dimension(600, 400));
        setBackground(Color.BLACK);
        setFocusable(true);

        // Input Handling: Send INTENT, not position
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) { sendInput("PRESS", e); }
            public void keyReleased(KeyEvent e) { sendInput("RELEASE", e); }
        });

        // Rendering Loop (High FPS for smoothness)
        new javax.swing.Timer(16, e -> {
            updateInterpolation();
            repaint();
        }).start();

        // Ping Loop (Every 1 second)
        new javax.swing.Timer(1000, e -> {
            if (out != null) {
                out.println("PING:" + System.currentTimeMillis());
            }
        }).start();

        connectToServer();
    }

    private void sendInput(String action, KeyEvent e) {
        String dir = null;
        if (e.getKeyCode() == KeyEvent.VK_W) dir = "UP";
        if (e.getKeyCode() == KeyEvent.VK_S) dir = "DOWN";
        if (e.getKeyCode() == KeyEvent.VK_A) dir = "LEFT";
        if (e.getKeyCode() == KeyEvent.VK_D) dir = "RIGHT";

        if (dir != null && out != null) {
            out.println(action + ":" + dir);
        }
    }

    private void connectToServer() {
        new Thread(() -> {
            try {
                Socket socket = new Socket(SERVER_IP, PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("PONG:")) {
                        // Calculate Ping
                        long sentTime = Long.parseLong(line.split(":")[1]);
                        currentPing = System.currentTimeMillis() - sentTime;
                    } else {
                        parseServerState(line);
                    }
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private void parseServerState(String line) {
        if (!line.startsWith("START|") || !line.endsWith("|END")) return;

        String[] parts = line.split("\\|");
        // part[0] is START
        // part[1] is Coin info
        String[] coinParts = parts[1].split(",");
        coinX = Integer.parseInt(coinParts[0]);
        coinY = Integer.parseInt(coinParts[1]);

        // Remaining parts are players
        Set<Integer> serverIds = new HashSet<>();
        for (int i = 2; i < parts.length - 1; i++) {
            String[] pData = parts[i].split(",");
            int id = Integer.parseInt(pData[0]);
            int x = Integer.parseInt(pData[1]);
            int y = Integer.parseInt(pData[2]);
            int score = Integer.parseInt(pData[3]);

            serverIds.add(id);
            entities.putIfAbsent(id, new Entity());
            Entity e = entities.get(id);
            e.targetX = x;
            e.targetY = y;
            e.score = score;
        }
    }

    // SMOOTHING / INTERPOLATION LOGIC
    private void updateInterpolation() {
        double smoothingFactor = 0.2;
        for (Entity e : entities.values()) {
            e.currentX = lerp(e.currentX, e.targetX, smoothingFactor);
            e.currentY = lerp(e.currentY, e.targetY, smoothingFactor);
        }
    }

    private double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Draw Coin
        g.setColor(Color.YELLOW);
        g.fillOval(coinX, coinY, 15, 15);

        // Draw Players
        for (Map.Entry<Integer, Entity> entry : entities.entrySet()) {
            Entity e = entry.getValue();
            g.setColor(Color.CYAN);
            g.fillRect((int)e.currentX, (int)e.currentY, 30, 30);

            g.setColor(Color.WHITE);
            g.drawString("P" + entry.getKey() + ": " + e.score, (int)e.currentX, (int)e.currentY - 5);
        }

        // Draw Latency (Ping)
        g.setColor(Color.GREEN);
        g.drawString("Ping: " + currentPing + "ms", 10, 20);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Multiplayer Coin Collector");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new GameClient());
        frame.pack();
        frame.setVisible(true);
    }
}