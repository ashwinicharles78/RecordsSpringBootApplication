package com.records.Records.model;

import com.records.Records.enums.Roles;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaUserData {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private List<Roles> roles;
}
