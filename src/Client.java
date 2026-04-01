import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Client {

    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Please provide the server address and port.");
            return;
        }

        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);

        ExecutorService executorService = Executors.newFixedThreadPool(4);

        System.out.println("Welcome to the File Explorer Client!");

        while (true) {
            System.out.println("\nCommands:");
            System.out.println("run list");
            System.out.println("run delete");
            System.out.println("run rename");
            System.out.println("run download");
            System.out.println("run upload");
            System.out.println("run quit");

            String command = scanner.nextLine().trim();

            if (command.equals("run quit")) {
                executorService.shutdownNow();
                System.out.println("Client shutting down...");
                break;
            }

            Runnable task = createTask(command, serverAddress, port);

            if (task != null) {
                executorService.submit(task);
            } else {
                System.out.println("Invalid command.");
            }
        }
    }

    private static Runnable createTask(String command, String host, int port) {
        try {
            switch (command) {
                case "run list":
                    return new Task((socket, reader) -> handleList(socket, reader), host, port);

                case "run delete":
                    System.out.print("Enter filename: ");
                    String delFile = scanner.nextLine();
                    return new Task((socket, reader) -> handleDelete(socket, reader, delFile), host, port);

                case "run rename":
                    System.out.print("Old name: ");
                    String oldName = scanner.nextLine();
                    System.out.print("New name: ");
                    String newName = scanner.nextLine();
                    return new Task((socket, reader) -> handleRename(socket, reader, oldName, newName), host, port);

                case "run download":
                    System.out.print("Filename: ");
                    String downFile = scanner.nextLine();
                    return new Task((socket, reader) -> handleDownload(socket, reader, downFile), host, port);

                case "run upload":
                    System.out.print("Filename: ");
                    String upFile = scanner.nextLine();
                    return new Task((socket, reader) -> handleUpload(socket, reader, upFile), host, port);

                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
    private static void printBlock(String title, String content) {
        String thread = Thread.currentThread().getName();

        System.out.println("\n==============================");
        System.out.println("[" + thread + "] " + title);
        System.out.println("------------------------------");
        System.out.print(content);
        System.out.println("==============================\n");
    }

    // ---------------- HANDLERS ----------------

    private static void handleList(Socket socket, BufferedReader reader) {
        delay();
        log("Listing files");
        StringBuilder sb = new StringBuilder();

        try {
            sendRequest(socket, "list", null);
            int count = Integer.parseInt(reader.readLine());

            if (count == 0) {
                sb.append("No files found.\n");
            } else {
                for (int i = 0; i < count; i++) {
                    sb.append((i + 1)).append(". ").append(reader.readLine()).append("\n");
                }
            }

        } catch (Exception e) {
            sb.append("ERROR: ").append(e.getMessage()).append("\n");
        }

        printBlock("LIST RESULT", sb.toString());
    }

    private static void handleDelete(Socket socket, BufferedReader reader, String file) {
        delay();
        log("Deleting file: " + file);
        StringBuilder sb = new StringBuilder();

        try {
            sendRequest(socket, "delete", file);
            sb.append(reader.readLine()).append("\n");
        } catch (Exception e) {
            sb.append("ERROR: ").append(e.getMessage()).append("\n");
        }

        printBlock("DELETE (" + file + ")", sb.toString());
    }

    private static void handleRename(Socket socket, BufferedReader reader, String oldName, String newName) {
        delay();
        log("Renaming " + oldName + " to " + newName);
        StringBuilder sb = new StringBuilder();

        try {
            sendRequest(socket, "rename", oldName + "?" + newName);
            sb.append(reader.readLine()).append("\n");
        } catch (Exception e) {
            sb.append("ERROR: ").append(e.getMessage()).append("\n");
        }

        printBlock("RENAME (" + oldName + " → " + newName + ")", sb.toString());
    }

    private static void handleDownload(Socket socket, BufferedReader reader, String file) {
        delay();
        log("Starting download: " + file);
        StringBuilder sb = new StringBuilder();

        try {
            sendRequest(socket, "download", file);
            String response = reader.readLine();

            if (response.startsWith("ERROR")) {
                sb.append(response).append("\n");
                printBlock("DOWNLOAD (" + file + ")", sb.toString());
                return;
            }

            String[] parts = response.split("\\?");
            long size = Long.parseLong(parts[1]);

            File out = new File("ClientFiles", file);
            out.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(out)) {
                byte[] buffer = new byte[4096];
                long total = 0;
                int read;

                while (total < size && (read = socket.getInputStream().read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    total += read;
                }
            }

            sb.append("Downloaded successfully: ").append(file).append("\n");

        } catch (Exception e) {
            sb.append("ERROR: ").append(e.getMessage()).append("\n");
        }

        printBlock("DOWNLOAD (" + file + ")", sb.toString());
    }

    private static void handleUpload(Socket socket, BufferedReader reader, String fileName) {
        delay();
        log("Starting upload: " + fileName);
        StringBuilder sb = new StringBuilder();

        try {
            File file = new File("ClientFiles", fileName);

            if (!file.exists()) {
                sb.append("File not found.\n");
                printBlock("UPLOAD (" + fileName + ")", sb.toString());
                return;
            }

            sendRequest(socket, "upload", fileName + "?" + file.length());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;

                while ((read = fis.read(buffer)) != -1) {
                    socket.getOutputStream().write(buffer, 0, read);
                }
            }

            sb.append(reader.readLine()).append("\n");

        } catch (Exception e) {
            sb.append("ERROR: ").append(e.getMessage()).append("\n");
        }

        printBlock("UPLOAD (" + fileName + ")", sb.toString());
    }


    private static void sendRequest(Socket socket, String cmd, String args) throws IOException {
        String msg = cmd + (args == null ? "" : "?" + args) + "\n";
        socket.getOutputStream().write(msg.getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }

    private static void delay() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException ignored) {}
    }

    private static void log(String msg) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + msg);
    }
}