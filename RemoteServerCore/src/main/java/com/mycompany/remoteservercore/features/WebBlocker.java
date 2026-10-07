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
}
