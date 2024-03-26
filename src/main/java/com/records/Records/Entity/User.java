package com.records.Records.Entity;

import com.records.Records.enums.Roles;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.List;


@Entity
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String firstName;
    private String lastName;
    @NonNull
    private String email;

    @Column(length = 60)
    @NonNull
    private String password;

    private List<Roles> roles;
    private boolean enabled = false;
}
