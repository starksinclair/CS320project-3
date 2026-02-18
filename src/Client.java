import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.BiConsumer;

public class Client {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Please provide the server address and port as arguments.");
            return;
        }
        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);
        Scanner scanner = new Scanner(System.in);
        Map<String, Map<String, BiConsumer<Socket, BufferedReader>>> commands = getCommandHandlers();

        System.out.println("Welcome to the File Explorer Client!");
        System.out.println();
        System.out.println("Available commands:");
        int count = 1;
        for (Map.Entry<String, Map<String, BiConsumer<Socket, BufferedReader>>> command : commands.entrySet()) {
            for (String desc : command.getValue().keySet()) {
                System.out.printf("  %d) %-15s : %s%n", count++, command.getKey(), desc);
            }
        }
        System.out.println();

        try (Socket socket = new Socket(serverAddress, port)) {
            System.out.println("Connected to the echo server at " + serverAddress + ":" + port);
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (true) {
                System.out.println("Enter a command to proceed (or 'run quit' to quit at any time). ");
                String input = scanner.nextLine();
                if (input == null || input.isBlank()) {
                    continue;
                }

                Map<String, BiConsumer<Socket, BufferedReader>> handlerMap = commands.get(input);
                if (handlerMap == null) {
                    System.out.println("Unknown command: " + input);
                    continue;
                }
                BiConsumer<Socket, BufferedReader> handler = handlerMap.values().iterator().next();
                handler.accept(socket, reader);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, Map<String, BiConsumer<Socket, BufferedReader>>> getCommandHandlers() {
        Map<String, Map<String, BiConsumer<Socket, BufferedReader>>> handlers = new HashMap<>();

        handlers.put("run list", Map.of("list <directory> - List files on the server", Client::handleGetListCommand));
        handlers.put("run delete", Map.of("delete <filename> - Remove a file on the server", Client::handleDeleteCommand));
        handlers.put("run rename", Map.of("rename <oldname> <newname> - Rename a file on the server", Client::handleRenameCommand));
        handlers.put("run download", Map.of("download <filename> - Download a file from the server", Client::handleDownloadCommand));
        handlers.put("run upload", Map.of("upload <filename> - Upload a file to the server", Client::handleUploadCommand));
        handlers.put("run quit", Map.of("quit - Exit the client application", Client::handleQuitCommand));

        return handlers;
    }

    private static void handleGetListCommand(Socket socket, BufferedReader reader) {
       try {
            listServerFiles(socket, reader);
           System.out.println("------------------------------------------------------------------");

       } catch (IOException e) {
           throw new RuntimeException(e);
       }
    }
    private static void handleDeleteCommand(Socket socket, BufferedReader reader) {
         try {
            listServerFiles(socket, reader);
            System.out.println("Enter the name of the file to delete from the server:");
            Scanner scanner = new Scanner(System.in);
            String fileName = scanner.nextLine();
            if (fileName.isBlank()) {
                System.out.println("No filename provided. Aborting delete.");
                return;
            }
            sendRequest(socket, "delete", fileName);
             System.out.println("deleting ....");
            String response = reader.readLine();
            if (response.startsWith("ERROR")) {
                System.out.println(response);
                return;
            }
            if (response.startsWith("OK")) {
                System.out.println("File deleted successfully.");
            } else {
                System.out.println("Unexpected server response: " + response);
            }
        } catch (IOException e) {
            System.err.println("I/O error during delete: " + e.getMessage());
            e.printStackTrace();
        }

    }
    private static void handleRenameCommand(Socket socket, BufferedReader reader) {
          try {
             listServerFiles(socket, reader);
             System.out.println("Enter the name of the file to rename:");
             Scanner scanner = new Scanner(System.in);
             String oldName = scanner.nextLine();
            if (oldName.isBlank()) {
                System.out.println("No filename provided. Aborting rename.");
                return;
            }
            System.out.println("Enter the new name for the file:");
            String newName = scanner.nextLine();
            if (newName.isBlank()) {
                System.out.println("No new filename provided. Aborting rename.");
                return;
            }
            sendRequest(socket, "rename", oldName + "?" + newName);
            System.out.println("renaming ....");
            String response = reader.readLine();
             if (response.startsWith("ERROR")) {
                 System.out.println(response);
                 return;
             }
             if (response.startsWith("OK")) {
                    System.out.println("File renamed successfully.");
                } else {
                    System.out.println("Unexpected server response: " + response);
             }
         } catch (IOException e) {
             System.err.println("I/O error during rename: " + e.getMessage());
             e.printStackTrace();
         }
    }
    private static void handleDownloadCommand(Socket socket, BufferedReader reader) {
        try {
            listServerFiles(socket, reader);
            System.out.println("Enter the name of the file to request from the server:");
            Scanner scanner = new Scanner(System.in);
            String fileName = scanner.nextLine();

            if (fileName.isBlank()) {
                System.out.println("No filename provided. Aborting download.");
                return;
            }

            sendRequest(socket, "download", fileName);
            System.out.println("downloading ....");
            String response = reader.readLine();
            if (response.startsWith("ERROR")) {
                System.out.println(response);
                return;
            }
            String[] parts = response.split("\\?");
            long fileSize = Long.parseLong(parts[1]);

            // Read file contents
            File downloadFolder = new File("ClientFiles");
            if (!downloadFolder.exists()) {
                downloadFolder.mkdirs();
            }

            File outputFile = new File(downloadFolder, fileName);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[4096];
                long totalRead = 0;
                int bytesRead;

                while (totalRead < fileSize && (bytesRead = socket.getInputStream().read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;
                }
            }

            System.out.println("File downloaded successfully to: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("I/O error during download: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static void handleUploadCommand(Socket socket, BufferedReader reader) {
        try {
            System.out.println("-------------------------------------------------------------------");
            System.out.println("Displaying files in the local ClientFiles directory:");
            System.out.println("-------------------------------------------------------------------");
            File uploadFolder = new File("ClientFiles");
            if (!uploadFolder.exists() || !uploadFolder.isDirectory()) {
                System.out.println("No ClientFiles directory found. Please create a ClientFiles directory and add files to upload.");
                return;
            }
            File[] localFiles = uploadFolder.listFiles();
            if (localFiles == null || localFiles.length == 0) {
                System.out.println("No files found in ClientFiles directory. Please add files to upload.");
                return;
            }
            for (int i = 0; i < localFiles.length; i++) {
                if (localFiles[i].isFile()) {
                    System.out.println((i + 1) + ". " + localFiles[i].getName());
                }
            }
            System.out.println("Enter the name of the file to upload to the server:");
            Scanner scanner = new Scanner(System.in);
            String fileName = scanner.nextLine();
            if (fileName.isBlank()) {
                System.out.println("No filename provided. Aborting upload.");
                return;
            }
            File fileToUpload = new File(uploadFolder, fileName);
            if (!fileToUpload.exists() || fileToUpload.isDirectory()) {
                System.out.println("File not found for upload: " + fileToUpload.getAbsolutePath());
                return;
            }
            long size = fileToUpload.length();
            sendRequest(socket, "upload", fileName + "?" + size);
            System.out.println("uploading ....");
            try (FileInputStream fis = new FileInputStream(fileToUpload)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    socket.getOutputStream().write(buffer, 0, bytesRead);
                }
                socket.getOutputStream().flush();
            }
            String response = reader.readLine();
            if (response.startsWith("ERROR")) {
                System.out.println(response);
                return;
            }
            if (response.startsWith("OK")) {
                System.out.println("File uploaded successfully.");
            } else {
                System.out.println("Unexpected server response: " + response);
            }
        }catch (IOException e) {
            System.err.println("Error during upload: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static void handleQuitCommand(Socket socket, BufferedReader reader) {
        System.out.println("Goodbye!");
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
        System.exit(0);
    }
    private static void listServerFiles(Socket socket, BufferedReader reader) throws IOException {
        System.out.println("-------------------------------------------------------------------");
        System.out.println("Displaying files on the server:");
        System.out.println("-------------------------------------------------------------------");
        sendRequest(socket, "list", null);

        // Read the count of files
        int fileCount = Integer.parseInt(reader.readLine());

        if (fileCount == 0) {
            System.out.println("No files found on server.");
        } else {
            // Read exactly that many filenames
            for (int i = 0; i < fileCount; i++) {
                String filename = reader.readLine();
                System.out.println((i + 1) + ". " + filename);
            }
        }
    }

    private static void sendRequest(Socket socket, String command, String args) throws IOException {
        OutputStream outputStream = socket.getOutputStream();

        String payload = command + (args == null ? "" : "?" + args) + "\n";
        outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
