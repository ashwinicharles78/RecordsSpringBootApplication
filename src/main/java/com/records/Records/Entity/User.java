package com.records.Records.Entity;

import com.records.Records.enums.Roles;
import jakarta.persistence.*;
import lombok.Data;

import java.util.List;


@Entity
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String firstName;
    private String lastName;
    private String email;

    @Column(length = 60)
    private String password;

    private List<Roles> roles;
    private boolean enabled = false;
}
