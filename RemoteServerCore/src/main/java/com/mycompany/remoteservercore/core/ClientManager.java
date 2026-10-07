/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.remoteservercore.core;

import java.util.ArrayList;
import java.util.List;

public class ClientManager {
    private static final List<ClientHandler> clients = new ArrayList<>();

    public static synchronized void addClient(ClientHandler client) {
        clients.add(client);
    }

    public static synchronized void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    public static synchronized void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }
}
