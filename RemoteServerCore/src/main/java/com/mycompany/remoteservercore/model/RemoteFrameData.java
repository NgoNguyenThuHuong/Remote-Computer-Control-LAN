package com.mycompany.remoteservercore.model;

import java.io.Serializable;

/**
 * Đối tượng truyền tải khung hình màn hình từ Client về Server trong phiên Remote Desktop.
 */
public class RemoteFrameData implements Serializable {
    private static final long serialVersionUID = 1L;

    private int width;
    private int height;
    private String base64Image;
    private long frameId;
    private long timestamp;

    public RemoteFrameData() {
        this.timestamp = System.currentTimeMillis();
    }

    public RemoteFrameData(int width, int height, String base64Image, long frameId) {
        this.width = width;
        this.height = height;
        this.base64Image = base64Image;
        this.frameId = frameId;
        this.timestamp = System.currentTimeMillis();
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getBase64Image() {
        return base64Image;
    }

    public void setBase64Image(String base64Image) {
        this.base64Image = base64Image;
    }

    public long getFrameId() {
        return frameId;
    }

    public void setFrameId(long frameId) {
        this.frameId = frameId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
