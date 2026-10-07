package com.rcc.database;

import com.rcc.model.Client;
import com.rcc.model.Log;
import com.rcc.model.User;

import java.util.List;

public class DatabaseTest {
    public static void main(String[] args) {
        System.out.println("Starting Database CRUD Test...");

        // Ensure database connection and tables are created
        DatabaseConnection.getConnection();

        testUserDAO();
        testClientDAO();
        testLogDAO();

        System.out.println("\nAll CRUD tests completed.");
    }

    private static void testUserDAO() {
        System.out.println("\n--- Testing UserDAO ---");
        UserDAO userDAO = new UserDAO();

        // Create
        User user = new User(0, "admin_" + System.currentTimeMillis(), "password123", "admin");
        System.out.println("Inserting User: " + userDAO.insertUser(user));

        // Read
        List<User> users = userDAO.getAllUsers();
        System.out.println("Total Users: " + users.size());
        if (!users.isEmpty()) {
            User lastUser = users.get(users.size() - 1);
            System.out.println("Retrieved User: " + userDAO.getUserById(lastUser.getId()));

            // Update
            lastUser.setPassword("newpassword456");
            System.out.println("Updating User: " + userDAO.updateUser(lastUser));
            System.out.println("Updated User: " + userDAO.getUserById(lastUser.getId()));

            // Delete
            System.out.println("Deleting User: " + userDAO.deleteUser(lastUser.getId()));
        }
    }

    private static void testClientDAO() {
        System.out.println("\n--- Testing ClientDAO ---");
        ClientDAO clientDAO = new ClientDAO();

        // Create
        Client client = new Client(0, "192.168.1.100", "00:1A:2B:3C:4D:" + (System.currentTimeMillis() % 100), "PC-01", "online");
        System.out.println("Inserting Client: " + clientDAO.insertClient(client));

        // Read
        List<Client> clients = clientDAO.getAllClients();
        System.out.println("Total Clients: " + clients.size());
        if (!clients.isEmpty()) {
            Client lastClient = clients.get(clients.size() - 1);
            System.out.println("Retrieved Client: " + clientDAO.getClientById(lastClient.getId()));

            // Update
            lastClient.setStatus("offline");
            System.out.println("Updating Client: " + clientDAO.updateClient(lastClient));
            System.out.println("Updated Client: " + clientDAO.getClientById(lastClient.getId()));
        }
    }

    private static void testLogDAO() {
        System.out.println("\n--- Testing LogDAO ---");
        LogDAO logDAO = new LogDAO();
        ClientDAO clientDAO = new ClientDAO();

        List<Client> clients = clientDAO.getAllClients();
        if (!clients.isEmpty()) {
            int clientId = clients.get(0).getId();

            // Create
            Log log = new Log(0, clientId, "Client locked", null);
            System.out.println("Inserting Log: " + logDAO.insertLog(log));

            // Read
            List<Log> logs = logDAO.getAllLogs();
            System.out.println("Total Logs: " + logs.size());
            
            List<Log> clientLogs = logDAO.getLogsByClientId(clientId);
            System.out.println("Logs for Client " + clientId + ": " + clientLogs.size());
            if (!clientLogs.isEmpty()) {
                System.out.println("Retrieved Log: " + clientLogs.get(0));
            }
        } else {
            System.out.println("No clients available to test LogDAO.");
        }
    }
}
