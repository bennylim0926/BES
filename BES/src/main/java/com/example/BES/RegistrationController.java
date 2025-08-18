package com.example.BES;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class RegistrationController {

    @Autowired
    RegistrationService service;

    @RequestMapping("/audition-order")
    @ResponseBody
    public int drawRandomNumber(){
        return service.drawRandomNumberService();
    }

    @RequestMapping("/validate-registration")
    @ResponseBody
    public boolean validateRegistration(){
        // it should be a qr consisting name, category and payment status 
        return service.validateRegistrationService();
    }

    public void addWalkInBattler(){
        service.increaseBattlersList();
    }
}
