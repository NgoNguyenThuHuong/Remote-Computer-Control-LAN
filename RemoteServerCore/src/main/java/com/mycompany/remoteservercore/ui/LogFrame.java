/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.database.LogDAO;
import com.mycompany.remoteservercore.model.LogEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class LogFrame extends JFrame {

    private JTable logTable;
    private DefaultTableModel tableModel;
    private JTextField txtClientFilter;
    private JComboBox<String> cbEventFilter;
    private JButton btnFilter;
    private JButton btnRefresh;

    public LogFrame() {
        initComponents();
        loadData();
    }

    private void initComponents() {
        setTitle("Lịch sử hoạt động (Activity Log)");
        setSize(800, 500);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Top Panel for Filters
        JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

        filterPanel.add(new JLabel("Client IP:"));
        txtClientFilter = new JTextField(15);
        filterPanel.add(txtClientFilter);

        filterPanel.add(new JLabel("Loại sự kiện:"));
        String[] events = {"All", "Client Connect", "Client Disconnect", "Command", "Command Result", "Chat", "Website Blocking", "Error", "Login", "Logout"};
        cbEventFilter = new JComboBox<>(events);
        filterPanel.add(cbEventFilter);

        btnFilter = new JButton("Lọc");
        btnRefresh = new JButton("Làm mới");
        
        filterPanel.add(btnFilter);
        filterPanel.add(btnRefresh);

        add(filterPanel, BorderLayout.NORTH);

        // Center Panel for Table
        tableModel = new DefaultTableModel(new String[]{"ID", "Thời gian", "Client IP", "Sự kiện", "Mô tả"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        logTable = new JTable(tableModel);
        
        // Adjust column widths
        logTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        logTable.getColumnModel().getColumn(1).setPreferredWidth(150);
        logTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        logTable.getColumnModel().getColumn(3).setPreferredWidth(150);
        logTable.getColumnModel().getColumn(4).setPreferredWidth(350);

        JScrollPane scrollPane = new JScrollPane(logTable);
        add(scrollPane, BorderLayout.CENTER);

        // Actions
        btnFilter.addActionListener(e -> applyFilter());
        btnRefresh.addActionListener(e -> {
            txtClientFilter.setText("");
            cbEventFilter.setSelectedIndex(0);
            loadData();
        });
    }

    private void loadData() {
        List<LogEntry> logs = LogDAO.getAllLogs();
        updateTable(logs);
    }

    private void applyFilter() {
        String clientFilter = txtClientFilter.getText();
        String eventFilter = (String) cbEventFilter.getSelectedItem();
        List<LogEntry> logs = LogDAO.getFilteredLogs(clientFilter, eventFilter);
        updateTable(logs);
    }

    private void updateTable(List<LogEntry> logs) {
        tableModel.setRowCount(0);
        for (LogEntry log : logs) {
            tableModel.addRow(new Object[]{
                    log.getId(),
                    log.getTimestamp(),
                    log.getClientIp(),
                    log.getEventType(),
                    log.getDescription()
            });
        }
    }
}
