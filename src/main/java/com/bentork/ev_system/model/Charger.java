package com.bentork.ev_system.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Charger {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "station_id", nullable = false)
	private Station station;

	@Column(nullable = false, unique = true)
	private String ocppId;

	@Column(nullable = false)
	private String connectorType;

	@Column(nullable = false)
	private String chargerType; // AC or DC

	@Column(nullable = false)
	private Double rate;

	@Column(name = "kw_output")
	private Double kwOutput; // e.g., 7.4, 11.0, 22.0, 50.0

	private boolean isOccupied;

	private boolean availability;

	private LocalDateTime createdAt = LocalDateTime.now();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Station getStation() {
		return station;
	}

	public void setStation(Station station) {
		this.station = station;
	}

	public String getOcppId() {
		return ocppId;
	}

	public void setOcppId(String ocppId) {
		this.ocppId = ocppId;
	}

	public String getConnectorType() {
		return connectorType;
	}

	public void setConnectorType(String connectorType) {
		this.connectorType = connectorType;
	}

	public String getChargerType() {
		return chargerType;
	}

	public void setChargerType(String chargerType) {
		this.chargerType = chargerType;
	}

	public Double getRate() {
		return rate;
	}

	public void setRate(Double rate) {
		this.rate = rate;
	}

	public boolean isOccupied() {
		return isOccupied;
	}

	public void setOccupied(boolean isOccupied) {
		this.isOccupied = isOccupied;
	}

	public boolean isAvailability() {
		return availability;
	}

	public void setAvailability(boolean availability) {
		this.availability = availability;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public Double getKwOutput() {
		return kwOutput;
	}

	public void setKwOutput(Double kwOutput) {
		this.kwOutput = kwOutput;
	}
}
