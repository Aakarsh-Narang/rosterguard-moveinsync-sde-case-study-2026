package com.rosterguard.api.dto;

import jakarta.validation.constraints.NotBlank;

public class TransportProfileUpdateRequest {

    @NotBlank(message = "preferredPickupPoint is required")
    private String preferredPickupPoint;

    private String specialAssistanceNeeds;

    public TransportProfileUpdateRequest() {}

    public TransportProfileUpdateRequest(String preferredPickupPoint, String specialAssistanceNeeds) {
        this.preferredPickupPoint = preferredPickupPoint;
        this.specialAssistanceNeeds = specialAssistanceNeeds;
    }

    public String getPreferredPickupPoint() { return preferredPickupPoint; }
    public void setPreferredPickupPoint(String preferredPickupPoint) { this.preferredPickupPoint = preferredPickupPoint; }

    public String getSpecialAssistanceNeeds() { return specialAssistanceNeeds; }
    public void setSpecialAssistanceNeeds(String specialAssistanceNeeds) { this.specialAssistanceNeeds = specialAssistanceNeeds; }
}
