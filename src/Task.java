import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

public class Task implements Runnable {

    private final BiConsumer<Socket, BufferedReader> handler;
    private final String host;
    private final int port;

    public Task(BiConsumer<Socket, BufferedReader> handler, String host, int port) {
        this.handler = handler;
        this.host = host;
        this.port = port;
    }

    @Override
    public void run() {
        try (Socket socket = new Socket(host, port);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            handler.accept(socket, reader);

        } catch (Exception e) {
            System.err.println("[" + Thread.currentThread().getName() + "] ERROR: " + e.getMessage());
        }
    }
}