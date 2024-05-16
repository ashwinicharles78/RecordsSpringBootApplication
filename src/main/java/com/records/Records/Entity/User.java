package com.records.Records.Entity;

import com.records.Records.enums.Roles;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;
import java.util.UUID;


@Entity
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String firstName;
    private String lastName;
    @NonNull
    @Column(unique = true)
    private String email;

    @Column(length = 60)
    @NonNull
    private String password;
//    @Column(unique = true)
    private String phoneNumber;
    private List<Roles> roles;
    private boolean enabled = false;
}
