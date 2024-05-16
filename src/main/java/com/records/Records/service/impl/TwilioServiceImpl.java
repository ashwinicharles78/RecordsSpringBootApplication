package com.records.Records.service.impl;

import com.records.Records.Entity.Otp;
import com.records.Records.Entity.User;
import com.records.Records.Repository.OtpRepository;
import com.records.Records.Repository.UserRepository;
import com.records.Records.config.TwilioConfig;
import com.records.Records.model.UserModel;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.Optional;
import java.util.Random;

@Service
public class TwilioServiceImpl {

    @Autowired
    private TwilioConfig twilioConfig;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    private final long EXPIRATION_TIME_MS = 10 * 60 * 1000;

    public void sendOtp(UserModel userModel){
        try {
            PhoneNumber to = new PhoneNumber(userModel.getPhoneNumber());
            PhoneNumber from = new PhoneNumber(twilioConfig.getTrialNumber());
            String otp = generateOTP();
            String otpMessage = "Dear Customer , Your OTP is ##" + otp + "##. Use this Passcode to complete your transaction. Thank You.";
            Message message = Message
                    .creator(to, from,
                            otpMessage)
                    .create();
            System.out.println("Otp sent successfully");
            long expirationTime = message.getDateCreated().toInstant().toEpochMilli() + EXPIRATION_TIME_MS;
            Otp saveOtp = new Otp(userModel.getPhoneNumber(), otp, expirationTime);
            otpRepository.save(saveOtp);
        } catch (Exception ex) {
            System.out.println("Failed to send OTP");
        }
    }

    public Boolean validateOtp(User userModel, String otpByUser) {
        Optional<Otp> otp = otpRepository.findById(userModel.getPhoneNumber());
        if (otp.isPresent()) {
            String otpFromDb = otp.get().getOtp();
            Long expirationTime = otp.get().getExpirationTime();
            if (otpFromDb.equals(otpByUser) && expirationTime != null && expirationTime > System.currentTimeMillis()) {
                User user = userRepository.findByEmail(userModel.getEmail());
                user.setEnabled(true);
                userRepository.save(user);
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    //6 digit otp
    private String generateOTP() {
        return new DecimalFormat("000000")
                .format(new Random().nextInt(999999));
    }

}
