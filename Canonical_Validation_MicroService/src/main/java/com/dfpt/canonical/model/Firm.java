package com.dfpt.canonical.model;

import jakarta.persistence.*;

@Entity
@Table(name = "firms")
public class Firm {
    @Id
    @Column(name = "firm_number")
    private Integer firmNumber;
    @Column(name = "firm_name")
    private String firmName;
    public Integer getFirmNumber() { return firmNumber; }
    public void setFirmNumber(Integer firmNumber) { this.firmNumber = firmNumber; }
    public String getFirmName() { return firmName; }
    public void setFirmName(String firmName) { this.firmName = firmName; }
}
