package com.records.Records.model;

import lombok.Data;

@Data
public class OtpRequestData {
    private String otp;
    private String phoneNumber;
}
