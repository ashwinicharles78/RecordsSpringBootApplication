package com.records.Records.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name="RECORDS", indexes = {@Index(name = "index_type_value", columnList = "type, value")})
public class Records {
    public Mykey getMykey() {
        return mykey;
    }

    public void setMykey(Mykey mykey) {
        this.mykey = mykey;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(LocalDateTime creationTime) {
        this.creationTime = creationTime;
    }

    @EmbeddedId
    private Mykey mykey;

    @Column(columnDefinition = "boolean default true")
    private Boolean active = true;

    @Column(name="creationTime")
    @CreationTimestamp
    private LocalDateTime creationTime;
}
