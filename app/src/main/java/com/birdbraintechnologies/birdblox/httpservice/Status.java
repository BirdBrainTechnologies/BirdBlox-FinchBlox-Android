package com.birdbraintechnologies.birdblox.httpservice;


/**
 * Enum for possible response statuses. Copied from NanoHttp:
 * https://github.com/NanoHttpd/nanohttpd/blob/b04a342a4bcd0ff44be65138acffc73a29381c72/core/src/main/java/org/nanohttpd/protocols/http/response/Status.java
 */
public enum Status {
    SWITCH_PROTOCOL(101, "Switching Protocols"),

    OK(200, "OK"),
    CREATED(201, "Created"),
    ACCEPTED(202, "Accepted"),
    NO_CONTENT(204, "No Content"),
    PARTIAL_CONTENT(206, "Partial Content"),
    MULTI_STATUS(207, "Multi-Status"),

    REDIRECT(301, "Moved Permanently"),

    BAD_REQUEST(400, "Bad Request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not Found"),
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
    NOT_ACCEPTABLE(406, "Not Acceptable"),
    REQUEST_TIMEOUT(408, "Request Timeout"),
    CONFLICT(409, "Conflict"),
    GONE(410, "Gone"),
    LENGTH_REQUIRED(411, "Length Required"),
    PRECONDITION_FAILED(412, "Precondition Failed"),
    PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported Media Type"),
    RANGE_NOT_SATISFIABLE(416, "Requested Range Not Satisfiable"),
    EXPECTATION_FAILED(417, "Expectation Failed"),
    TOO_MANY_REQUESTS(429, "Too Many Requests"),

    INTERNAL_ERROR(500, "Internal Server Error"),
    NOT_IMPLEMENTED(501, "Not Implemented"),
    SERVICE_UNAVAILABLE(503, "Service Unavailable"),
    UNSUPPORTED_HTTP_VERSION(505, "HTTP Version Not Supported");

    private final int requestStatus;

    private final String description;

    Status(int requestStatus, String description) {
        this.requestStatus = requestStatus;
        this.description = description;
    }

    public static Status lookup(int requestStatus) {
        for (Status status : Status.values()) {
            if (status.getRequestStatus() == requestStatus) {
                return status;
            }
        }
        return null;
    }

    public String getDescription() {
        return "" + this.requestStatus + " " + this.description;
    }

    public int getRequestStatus() {
        return this.requestStatus;
    }
}