/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.remoteservercore.features;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class WebBlocker {
    private static final String FILE_PATH = "blocked_websites.txt";
    private List<String> blockedWebsites;

    public WebBlocker() {
        blockedWebsites = new ArrayList<>();
        loadWebsites();
    }

    public void addWebsite(String domain) {
        if (!blockedWebsites.contains(domain)) {
            blockedWebsites.add(domain);
            saveWebsites();
        }
    }

    public void removeWebsite(String domain) {
        if (blockedWebsites.contains(domain)) {
            blockedWebsites.remove(domain);
            saveWebsites();
        }
    }

    public List<String> getBlockedWebsites() {
        return new ArrayList<>(blockedWebsites);
    }

    private void loadWebsites() {
        blockedWebsites.clear();
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    blockedWebsites.add(line.trim());
                }
            }
        } catch (IOException e) {
            System.err.println("[WebBlocker] Error loading websites: " + e.getMessage());
        }
    }

    private void saveWebsites() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_PATH))) {
            for (String domain : blockedWebsites) {
                writer.write(domain);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("[WebBlocker] Error saving websites: " + e.getMessage());
        }
    }

    public String generateConfigCommand() {
        StringBuilder sb = new StringBuilder("BLOCK_WEB:");
        for (int i = 0; i < blockedWebsites.size(); i++) {
            sb.append(blockedWebsites.get(i));
            if (i < blockedWebsites.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    public static final String HOSTS_FILE_WINDOWS = "C:\\Windows\\System32\\drivers\\etc\\hosts";
    public static final String HOSTS_FILE_LINUX = "/etc/hosts";
    public static final String BLOCK_MARKER_START = "# RCC_BLOCKED_WEBSITES_START";
    public static final String BLOCK_MARKER_END = "# RCC_BLOCKED_WEBSITES_END";

    /**
     * Áp dụng chặn danh sách tên miền vào file hosts của hệ điều hành trên máy Client.
     * Cần quyền Administrator trên Windows hoặc root trên Linux để ghi vào hosts.
     *
     * @param domains Danh sách domain cần chặn
     * @param statusMsg Bộ đệm trả về thông điệp kết quả
     * @return true nếu thành công
     */
    public static boolean applyHostsBlock(List<String> domains, StringBuilder statusMsg) {
        String os = System.getProperty("os.name").toLowerCase();
        File hostsFile = new File(os.contains("win") ? HOSTS_FILE_WINDOWS : HOSTS_FILE_LINUX);

        if (!hostsFile.exists()) {
            statusMsg.append("Không tìm thấy file hosts tại: ").append(hostsFile.getAbsolutePath());
            return false;
        }

        List<String> remainingLines = new ArrayList<>();
        boolean insideBlock = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(hostsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().equalsIgnoreCase(BLOCK_MARKER_START)) {
                    insideBlock = true;
                    continue;
                }
                if (line.trim().equalsIgnoreCase(BLOCK_MARKER_END)) {
                    insideBlock = false;
                    continue;
                }
                if (!insideBlock) {
                    remainingLines.add(line);
                }
            }
        } catch (IOException e) {
            statusMsg.append("Không thể đọc file hosts: ").append(e.getMessage());
            return false;
        }

        // Tạo nội dung mới
        List<String> newLines = new ArrayList<>(remainingLines);
        if (domains != null && !domains.isEmpty()) {
            newLines.add(BLOCK_MARKER_START);
            for (String raw : domains) {
                String domain = cleanDomain(raw);
                if (!domain.isEmpty()) {
                    newLines.add("127.0.0.1 " + domain);
                    if (!domain.startsWith("www.")) {
                        newLines.add("127.0.0.1 www." + domain);
                    }
                }
            }
            newLines.add(BLOCK_MARKER_END);
        }

        // Ghi lại vào file hosts
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(hostsFile))) {
            for (String l : newLines) {
                writer.write(l);
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            statusMsg.append("Lỗi ghi file hosts (Cần chạy Client bằng 'Run as Administrator'): ").append(e.getMessage());
            return false;
        }

        // Flush DNS Cache trên Windows
        if (os.contains("win")) {
            try {
                Process p = new ProcessBuilder("ipconfig", "/flushdns").start();
                p.waitFor();
            } catch (Exception ignored) {}
        }

        int count = (domains != null) ? domains.size() : 0;
        statusMsg.append("Đã áp dụng chặn ").append(count).append(" website và làm mới DNS thành công.");
        return true;
    }

    private static String cleanDomain(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase();
        if (s.startsWith("http://")) s = s.substring(7);
        if (s.startsWith("https://")) s = s.substring(8);
        int slash = s.indexOf('/');
        if (slash != -1) s = s.substring(0, slash);
        int colon = s.indexOf(':');
        if (colon != -1) s = s.substring(0, colon);
        return s.trim();
    }
}
