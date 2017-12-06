package com.tramchester.domain.liveUpdates;

public class DueTram {
    private String status;
    private String destination;
    private int wait;
    private String carriages;

    public DueTram() {
        // deserialisation
    }

    public DueTram(String destination, String status, int wait, String carriages) {
        this.destination = destination;
        this.status = status;
        this.wait = wait;
        this.carriages = carriages;
    }

    public String getDestination() {
        return destination;
    }

    public String getStatus() {
        return status;
    }

    public int getWait() {
        return wait;
    }

    public String getCarriages() {
        return carriages;
    }

    @Override
    public String toString() {
        return "DueTram{" +
                "status='" + status + '\'' +
                ", destination='" + destination + '\'' +
                ", wait=" + wait +
                ", carriages='" + carriages + '\'' +
                '}';
    }
}
