package com.records.Records.model;

import com.records.Records.enums.Roles;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class KafkaUserData {
    private String firstName;
    private String lastName;
    private String email;
    private List<Roles> roles;
}
