

import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;

public class ClientAcceptor implements Runnable {

    private final ServerSocketChannel listenChannel;
    private final ExecutorService es;

    public ClientAcceptor(ServerSocketChannel listenChannel, ExecutorService es) {
        this.listenChannel = listenChannel;
        this.es = es;
    }

    @Override
    public void run() {
        while (true) {
            try {
                SocketChannel serveChannel = listenChannel.accept();
                System.out.println("Client connected: " + serveChannel.getRemoteAddress());

                // Assign client to thread pool
                es.submit(new ClientHandler(serveChannel));

            } catch (AsynchronousCloseException e) {
                // Happens when the server shuts down
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}