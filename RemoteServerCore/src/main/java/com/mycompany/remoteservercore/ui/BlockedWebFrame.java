package com.mycompany.remoteservercore.ui;

import com.mycompany.remoteservercore.features.WebBlocker;
import com.mycompany.remoteservercore.database.LogDAO;
import com.mycompany.remoteservercore.model.LogEntry;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class BlockedWebFrame extends JFrame {
    private WebBlocker webBlocker;
    private DefaultListModel<String> listModel;
    private JList<String> listWebsites;
    private JTextField txtDomain;
    private JButton btnAdd;
    private JButton btnRemove;
    private JButton btnApply;

    public BlockedWebFrame() {
        webBlocker = new WebBlocker();
        initComponents();
        loadData();
    }

    private void initComponents() {
        setTitle("Quản lý Website Bị Chặn");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        listModel = new DefaultListModel<>();
        listWebsites = new JList<>(listModel);
        JScrollPane scrollPane = new JScrollPane(listWebsites);
        add(scrollPane, BorderLayout.CENTER);

        JPanel panelBottom = new JPanel();
        panelBottom.setLayout(new FlowLayout());

        txtDomain = new JTextField(15);
        btnAdd = new JButton("Thêm");
        btnRemove = new JButton("Xóa");
        btnApply = new JButton("Áp dụng (Gửi Client)");

        panelBottom.add(new JLabel("Domain: "));
        panelBottom.add(txtDomain);
        panelBottom.add(btnAdd);
        panelBottom.add(btnRemove);

        JPanel panelApply = new JPanel();
        panelApply.add(btnApply);

        JPanel panelContainer = new JPanel(new BorderLayout());
        panelContainer.add(panelBottom, BorderLayout.CENTER);
        panelContainer.add(panelApply, BorderLayout.SOUTH);

        add(panelContainer, BorderLayout.SOUTH);

        btnAdd.addActionListener(e -> {
            String domain = txtDomain.getText().trim();
            if (!domain.isEmpty()) {
                webBlocker.addWebsite(domain);
                loadData();
                txtDomain.setText("");
                logAction("Đã thêm website chặn: " + domain);
            }
        });

        btnRemove.addActionListener(e -> {
            String selected = listWebsites.getSelectedValue();
            if (selected != null) {
                webBlocker.removeWebsite(selected);
                loadData();
                logAction("Đã xóa website chặn: " + selected);
            }
        });

        btnApply.addActionListener(e -> {
            String configCmd = webBlocker.generateConfigCommand();
            System.out.println("[Server] Broadcasting config to clients: " + configCmd);
            com.mycompany.remoteservercore.core.ClientManager.broadcast(configCmd);
            JOptionPane.showMessageDialog(this, "Đã gửi cấu hình xuống Client:\n" + configCmd);
            logAction("Gửi cấu hình chặn web đến tất cả Client.");
        });
    }

    private void loadData() {
        listModel.clear();
        List<String> websites = webBlocker.getBlockedWebsites();
        for (String w : websites) {
            listModel.addElement(w);
        }
    }
    
    private void logAction(String action) {
        System.out.println("[LOG] " + action);
        LogDAO.saveLog(new LogEntry("Server", "Website Blocking", action));
    }
}
