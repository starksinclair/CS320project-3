import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.function.BiConsumer;

public class Server {
    public static void main(String[] args) {
        int port = 3002;

        System.out.printf("Echo server is running on port %d%n", port);
        try(ServerSocket my_socket = new ServerSocket(port)) {
            while (true) {
                System.out.println("Waiting for a client...");
                Socket clientSocket = my_socket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream();

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while ((line = reader.readLine()) != null){
                    System.out.println("Client cmd: " + line);
                    Map<String, BiConsumer<String, Socket>> handlers = initializeCommandHandlers();
                    String command = line.split(" ")[0];
                    String argsString = line.substring(command.length()).trim();
                    BiConsumer<String, Socket> handler= handlers.get(command);
                    if (handler == null) {
                        System.out.println("Unknown command: " + command);
                    }else {
                        handler.accept(argsString, clientSocket);
                    }
                }

                clientSocket.close();
                System.out.println("Client disconnected.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, BiConsumer<String, Socket>> initializeCommandHandlers() {
        Map<String, BiConsumer<String, Socket>> handlers = new java.util.HashMap<>();
        handlers.put("list", Server::handleListCommand);
        handlers.put("delete", Server::handleDeleteCommand);
        handlers.put("rename", Server::handleRenameCommand);
        handlers.put("download", Server::handleDownloadCommand);
        handlers.put("upload", Server::handleUploadCommand);
        handlers.put("quit", Server::handleQuitCommand);
        return handlers;
    }

    private static void handleDeleteCommand(String s, Socket socket) {
        System.out.println("Deleting file with args: " + s);
        try {
            String fileName = s.trim();
            OutputStream outputStream = socket.getOutputStream();
            File myFolder = new File("ServerFiles");
            File fileToDelete = new File(myFolder, fileName);
            if (!fileToDelete.exists()) {
                outputStream.write(("ERROR: File not found: " + fileName + "\n").getBytes());
                outputStream.flush();
                return;
            }
            boolean success = fileToDelete.delete();
            if (success) {
                outputStream.write(("OK: File deleted: " + fileName + "\n").getBytes());
            } else {
                outputStream.write(("ERROR: Failed to delete file: " + fileName + "\n").getBytes());
            }
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleRenameCommand(String s, Socket socket) {
        System.out.println("Renaming file with args: " + s);
       try {
            OutputStream outputStream = socket.getOutputStream();
           String [] parts = s.split(" ");
           if (parts.length != 2) {
               outputStream.write("ERROR: rename command requires exactly 2 arguments: oldname and newname\n".getBytes());
               outputStream.flush();
           }
           String oldName = parts[0];
           String newName = parts[1];
           File myFolder = new File("ServerFiles");
           File oldFile = new File(myFolder, oldName);
          if (!oldFile.exists()) {
                outputStream.write(("ERROR: File not found: " + oldName + "\n").getBytes());
                outputStream.flush();
                return;
          }
          File newFile = new File(myFolder, newName);
            if (newFile.exists()) {
                outputStream.write(("ERROR: A file with the new name already exists: " + newName + "\n").getBytes());
                outputStream.flush();
                return;
            }
            boolean success = oldFile.renameTo(newFile);
            if (success) {
                outputStream.write(("OK: File renamed from " + oldName + " to " + newName + "\n").getBytes());
            } else {
                outputStream.write(("ERROR: Failed to rename file " + oldName + " to " + newName + "\n").getBytes());
            }
            outputStream.flush();
       }catch (IOException e) {
           throw new RuntimeException(e);
       }
    }

    private static void handleListCommand(String s, Socket socket) {
        System.out.println("sending files");
        try {
            OutputStream outputStream = socket.getOutputStream();
            File folder = new File("ServerFiles");
            File[] files = folder.listFiles();

            if (files == null || files.length == 0) {
                outputStream.write("0\n".getBytes());
                outputStream.flush();
                return;
            }
            // Send count first, so the client knows how many filenames to expect
            outputStream.write((files.length + "\n").getBytes());

            // Send each filename
            StringBuilder fileList = new StringBuilder();
            for (File file : files) {
                if (file.isFile()) {
                    fileList.append(file.getName()).append("\n");
                }
            }

            outputStream.write(fileList.toString().getBytes());
            outputStream.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleUploadCommand(String s, Socket socket) {
        System.out.println("Uploading file with args: " + s);
        try {
            OutputStream outputStream = socket.getOutputStream();
            String [] parts = s.split(" ");
            if (parts.length != 2) {
                outputStream.write("ERROR: upload command requires exactly 2 arguments: filename and filesize\n".getBytes());
                outputStream.flush();
            }
            String fileName = parts[0];
            long fileSize = Long.parseLong(parts[1]);
            File myFolder = new File("ServerFiles");
            if (!myFolder.exists()) {
                myFolder.mkdirs();
            }
            File newFile = new File(myFolder, fileName);
            try (FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {
                byte[] buffer = new byte[4096];
                long bytesReceived = 0;
               int bytesRead;
                while (bytesReceived < fileSize && (bytesRead = socket.getInputStream().read(buffer)) != -1 ) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                    bytesReceived += bytesRead;
                }
            }
            outputStream.write(("OK: File uploaded: " + fileName + "\n").getBytes());
            outputStream.flush();
        }catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    private static void handleQuitCommand(String s, Socket socket) {
        System.out.println("Client requested disconnect");
        System.exit(0);
    }

    private static void handleDownloadCommand(String fileName, Socket socket) {
       try {
              OutputStream outputStream = socket.getOutputStream();
           File myFolder = new File("ServerFiles");
           if (!myFolder.exists()) {
               myFolder.mkdirs();
           }
           System.out.println("Client requested file " + fileName);
           File file = new File(myFolder, fileName);
           if (!file.exists()) {
               outputStream.write("ERROR: File not found\n".getBytes());
               outputStream.flush();
               return;
           }

           if (file.isDirectory()) {
               outputStream.write("ERROR: Cannot download a directory\n".getBytes());
               outputStream.flush();
               return;
           }

           // Send file size first
           long fileSize = file.length();
           outputStream.write(("OK " + fileSize + "\n").getBytes());
           outputStream.flush();

           // Send file contents
           try (FileInputStream fileInputStream = new FileInputStream(file)) {
               byte[] buffer = new byte[4096];
               int bytesRead;
               while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                   outputStream.write(buffer, 0, bytesRead);
               }
           }
           outputStream.flush();
           System.out.println("File sent successfully.");
       } catch (IOException e) {
           throw new RuntimeException(e);
       }
//        if (!my_file.exists() && !my_file.isDirectory()) {
//            try(FileInputStream fileInputStream = new FileInputStream(my_file)) {
//                byte[] fileBuffer = new byte[1024];
//                while ((bytesRead=fileInputStream.read(fileBuffer)) != -1) {
//                    outputStream.write(fileBuffer, 0, bytesRead);
//                }
//            }
//        } else if (!my_file.exists()) {
//            System.out.println("File not found on server: " + my_file.getAbsolutePath());
//        }else {
//            System.out.println("Requested file is a directory, not a file: " + my_file.getAbsolutePath());
        }
}