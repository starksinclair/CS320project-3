
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class ClientHandler implements Runnable {

    private final SocketChannel serveChannel;

    public ClientHandler(SocketChannel serveChannel) {
        this.serveChannel = serveChannel;
    }

    @Override
    public void run() {
        try (
                InputStream in = Channels.newInputStream(serveChannel);
                OutputStream out = Channels.newOutputStream(serveChannel);
                BufferedInputStream clientIn = new BufferedInputStream(in)
        ) {

            log("Client connected: " + serveChannel.getRemoteAddress());

            String line;

            while ((line = readLine(clientIn)) != null) {

                log("Received command: " + line);

                Map<String, BiConsumer<String, Context>> handlers = initializeHandlers();

                String[] parts = line.split("\\?", 2);
                String command = parts[0];
                String args = parts.length > 1 ? parts[1] : "";

                BiConsumer<String, Context> handler = handlers.get(command);

                if (handler == null) {
                    out.write("ERROR: Unknown command\n".getBytes());
                    out.flush();
                } else {
                    handler.accept(args, new Context(out, clientIn));
                }
            }

        } catch (Exception e) {
            log("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                serveChannel.close();
            } catch (IOException ignored) {}
            log("Client disconnected.");
        }
    }

    static class Context {
        OutputStream out;
        BufferedInputStream in;

        Context(OutputStream out, BufferedInputStream in) {
            this.out = out;
            this.in = in;
        }
    }

    private Map<String, BiConsumer<String, Context>> initializeHandlers() {
        Map<String, BiConsumer<String, Context>> map = new HashMap<>();
        map.put("list", this::handleList);
        map.put("delete", this::handleDelete);
        map.put("rename", this::handleRename);
        map.put("upload", this::handleUpload);
        map.put("download", this::handleDownload);
        return map;
    }

    private void simulateDelay(String command) {
        try {
            log("START " + command + " (simulating 4s work)");
            Thread.sleep(5000);
            log("END " + command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void log(String message) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + message);
    }

    private void handleList(String args, Context ctx) {
        simulateDelay("LIST");

        try {
            File folder = new File("ServerFiles");
            File[] files = folder.listFiles();

            if (files == null || files.length == 0) {
                ctx.out.write("0\n".getBytes());
                ctx.out.flush();
                return;
            }

            ctx.out.write((files.length + "\n").getBytes());

            for (File f : files) {
                if (f.isFile()) {
                    ctx.out.write((f.getName() + "\n").getBytes());
                }
            }

            ctx.out.flush();

        } catch (IOException e) {
            log("LIST error: " + e.getMessage());
        }
    }

    private void handleDelete(String fileName, Context ctx) {
        simulateDelay("DELETE");

        try {
            File file = new File("ServerFiles", fileName.trim());

            if (!file.exists()) {
                ctx.out.write("ERROR: File not found\n".getBytes());
            } else if (file.delete()) {
                ctx.out.write("OK: File deleted\n".getBytes());
            } else {
                ctx.out.write("ERROR: Delete failed\n".getBytes());
            }

            ctx.out.flush();

        } catch (IOException e) {
            log("DELETE error: " + e.getMessage());
        }
    }

    private void handleRename(String args, Context ctx) {
        simulateDelay("RENAME");

        try {
            String[] parts = args.split("\\?");
            if (parts.length != 2) {
                ctx.out.write("ERROR: rename requires old?new\n".getBytes());
                ctx.out.flush();
                return;
            }

            File oldFile = new File("ServerFiles", parts[0]);
            File newFile = new File("ServerFiles", parts[1]);

            if (!oldFile.exists()) {
                ctx.out.write("ERROR: Old file not found\n".getBytes());
            } else if (newFile.exists()) {
                ctx.out.write("ERROR: New file already exists\n".getBytes());
            } else if (oldFile.renameTo(newFile)) {
                ctx.out.write("OK: File renamed\n".getBytes());
            } else {
                ctx.out.write("ERROR: Rename failed\n".getBytes());
            }

            ctx.out.flush();

        } catch (IOException e) {
            log("RENAME error: " + e.getMessage());
        }
    }

    private void handleUpload(String args, Context ctx) {
        simulateDelay("UPLOAD");

        try {
            String[] parts = args.split("\\?");
            if (parts.length != 2) {
                ctx.out.write("ERROR: upload requires filename?size\n".getBytes());
                ctx.out.flush();
                return;
            }

            String fileName = parts[0];
            long fileSize = Long.parseLong(parts[1]);

            File folder = new File("ServerFiles");
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, fileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[4096];
                long received = 0;

                while (received < fileSize) {
                    int toRead = (int) Math.min(buffer.length, fileSize - received);
                    int bytesRead = ctx.in.read(buffer, 0, toRead);

                    if (bytesRead == -1) {
                        throw new IOException("Unexpected end of stream");
                    }

                    fos.write(buffer, 0, bytesRead);
                    received += bytesRead;
                }
            }

            ctx.out.write(("OK: Uploaded " + fileName + "\n").getBytes());
            ctx.out.flush();

        } catch (Exception e) {
            log("UPLOAD error: " + e.getMessage());
        }
    }

    private void handleDownload(String fileName, Context ctx) {
        simulateDelay("DOWNLOAD");

        try {
            File file = new File("ServerFiles", fileName);

            if (!file.exists()) {
                ctx.out.write("ERROR: File not found\n".getBytes());
                ctx.out.flush();
                return;
            }

            long fileSize = file.length();
            ctx.out.write(("OK?" + fileSize + "\n").getBytes());
            ctx.out.flush();

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    ctx.out.write(buffer, 0, bytesRead);
                }
            }

            ctx.out.flush();

        } catch (IOException e) {
            log("DOWNLOAD error: " + e.getMessage());
        }
    }

    private String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;

        while ((b = in.read()) != -1) {
            if (b == '\n') return sb.toString();
            if (b != '\r') sb.append((char) b);
        }

        return !sb.isEmpty() ? sb.toString() : null;
    }
}