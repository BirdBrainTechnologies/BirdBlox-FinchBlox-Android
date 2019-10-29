package com.birdbraintechnologies.birdblox.httpservice;

public class NativeAndroidResponse {

    private Status status;
    private String body;

    public NativeAndroidResponse (Status status, String body) {

        this.status = status;
        this.body = body;
    }


    public String getStatus() { return Integer.toString(status.getRequestStatus()); }
    public String getBody() { return body; }
}

