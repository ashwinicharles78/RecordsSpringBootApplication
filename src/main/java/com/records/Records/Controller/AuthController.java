package com.records.Records.Controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.records.Records.Entity.User;
import com.records.Records.helper.JwtHelper;
import com.records.Records.model.JwtRequest;
import com.records.Records.model.JwtResponse;
import com.records.Records.model.KafkaUserData;
import com.records.Records.model.OtpRequestData;
import com.records.Records.model.UserModel;
import com.records.Records.service.KafkaMessagePublisherService;
import com.records.Records.service.UserService;
import com.records.Records.service.impl.TwilioServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * @author Ashwini Charles on 3/10/2024
 * @project RecordsSpringBootApplication
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private KafkaMessagePublisherService kafkaMessagePublisherService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private AuthenticationManager manager;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtHelper helper;

    @Autowired
    private Environment env;

    @Autowired
    private TwilioServiceImpl twilioService;

    @Autowired
    private ObjectMapper objectMapper;


    private final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private static final String TOPIC_NAME = "kafka.topic.name";

    @PostMapping("/register")
    public String register(@RequestBody UserModel user) {
        if(Objects.nonNull(user)) {
            if(null == user.getEmail())
                return "Email cannot be null";
            if(null == user.getPassword())
                return "Password cannot be null";

            userService.registerUser(user);
            logger.info(user.getEmail() + " User saved ");

            twilioService.sendOtp(user);
            return "Success";
        }
        return "Please use proper format for user";
    }
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody JwtRequest request) {

        this.doAuthenticate(request.getEmail(), request.getPassword());


        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = this.helper.generateToken(userDetails);

        JwtResponse response = JwtResponse.builder()
                .jwtToken(token)
                .username(userDetails.getUsername()).build();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "Health OK";
    }

    private void doAuthenticate(String email, String password) {

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(email, password);
        try {
            manager.authenticate(authentication);


        } catch (BadCredentialsException e) {
            throw new BadCredentialsException(" Invalid Username or Password  !!");
        }

    }

    @GetMapping("/verifyUser")
    public String verifyRegisteration(@RequestBody OtpRequestData otp) throws IOException {
        User user = userService.getUserByPhone(otp.getPhoneNumber());
        Boolean isValid = twilioService.validateOtp(user, otp.getOtp());
        if (isValid) {
            KafkaUserData userData = objectMapper.readValue(objectMapper.writeValueAsBytes(user), KafkaUserData.class);
            kafkaMessagePublisherService.sendMessageToTopic(userData, env.getProperty(TOPIC_NAME));
            return "Success";
        }
        else {
            return "Failed";
        }
    }

    @ExceptionHandler(BadCredentialsException.class)
    public String exceptionHandler() {
        return "Credentials Invalid !!";
    }

}
