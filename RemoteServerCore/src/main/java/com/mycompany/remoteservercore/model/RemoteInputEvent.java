package com.mycompany.remoteservercore.model;

import java.io.Serializable;

/**
 * Gói tin biểu diễn thao tác Chuột và Bàn phím gửi từ Server tới Client để điều khiển từ xa.
 */
public class RemoteInputEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String ACTION_MOUSE_MOVE    = "MOUSE_MOVE";
    public static final String ACTION_MOUSE_PRESS   = "MOUSE_PRESS";
    public static final String ACTION_MOUSE_RELEASE = "MOUSE_RELEASE";
    public static final String ACTION_MOUSE_WHEEL   = "MOUSE_WHEEL";
    public static final String ACTION_KEY_PRESS     = "KEY_PRESS";
    public static final String ACTION_KEY_RELEASE   = "KEY_RELEASE";

    private String action;
    private int x;
    private int y;
    private int button;
    private int keyCode;
    private int wheelRotation;

    public RemoteInputEvent() {
    }

    public static RemoteInputEvent mouseMove(int x, int y) {
        RemoteInputEvent ev = new RemoteInputEvent();
        ev.action = ACTION_MOUSE_MOVE;
        ev.x = x;
        ev.y = y;
        return ev;
    }

    public static RemoteInputEvent mousePress(int x, int y, int button) {
        RemoteInputEvent ev = new RemoteInputEvent();
        ev.action = ACTION_MOUSE_PRESS;
        ev.x = x;
        ev.y = y;
        ev.button = button;
        return ev;
    }

    public static RemoteInputEvent mouseRelease(int x, int y, int button) {
        RemoteInputEvent ev = new RemoteInputEvent();
        ev.action = ACTION_MOUSE_RELEASE;
        ev.x = x;
        ev.y = y;
        ev.button = button;
        return ev;
    }

    public static RemoteInputEvent mouseWheel(int x, int y, int wheelRotation) {
        RemoteInputEvent ev = new RemoteInputEvent();
        ev.action = ACTION_MOUSE_WHEEL;
        ev.x = x;
        ev.y = y;
        ev.wheelRotation = wheelRotation;
        return ev;
    }

    public static RemoteInputEvent keyPress(int keyCode) {
        RemoteInputEvent ev = new RemoteInputEvent();
        ev.action = ACTION_KEY_PRESS;
        ev.keyCode = keyCode;
        return ev;
    }

    public static RemoteInputEvent keyRelease(int keyCode) {
        RemoteInputEvent ev = new RemoteInputEvent();
        ev.action = ACTION_KEY_RELEASE;
        ev.keyCode = keyCode;
        return ev;
    }

    // Getters and Setters
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getButton() {
        return button;
    }

    public void setButton(int button) {
        this.button = button;
    }

    public int getKeyCode() {
        return keyCode;
    }

    public void setKeyCode(int keyCode) {
        this.keyCode = keyCode;
    }

    public int getWheelRotation() {
        return wheelRotation;
    }

    public void setWheelRotation(int wheelRotation) {
        this.wheelRotation = wheelRotation;
    }
}
