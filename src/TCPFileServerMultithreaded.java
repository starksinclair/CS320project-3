

import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPFileServerMultithreaded {
    public static void main(String[] args) throws Exception {

        ServerSocketChannel listenChannel = ServerSocketChannel.open();
        listenChannel.bind(new InetSocketAddress(3002));

        System.out.println("Server running on port 3002...");

        ExecutorService es = Executors.newFixedThreadPool(8);

        es.submit(new ClientAcceptor(listenChannel, es));

        Scanner keyboard = new Scanner(System.in);
        String input;

        do {
            System.out.println("Type Q to quit.");
            input = keyboard.nextLine();
        } while (!input.equalsIgnoreCase("Q"));

        System.out.println("Shutting down server...");
        listenChannel.close();
        es.shutdown();
    }
}