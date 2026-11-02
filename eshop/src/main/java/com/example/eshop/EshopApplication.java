package com.example.eshop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class EshopApplication {
    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(EshopApplication.class, args);
    }

    /**
     * Tự động nạp file .env từ thư mục hiện tại hoặc thư mục cha nếu có
     */
    private static void loadDotEnv() {
        Path[] possiblePaths = new Path[]{
                Paths.get(".env"),
                Paths.get("../.env")
        };

        for (Path path : possiblePaths) {
            if (Files.exists(path) && !Files.isDirectory(path)) {
                try (BufferedReader reader = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        String key = line.substring(0, eqIdx).trim();
                        String val = line.substring(eqIdx + 1).trim();
                        // Bỏ dấu nháy kép hoặc đơn nếu có bọc quanh giá trị
                        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                    break;
                } catch (Exception ignored) {}
            }
        }
    }
}
