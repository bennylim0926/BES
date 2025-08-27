package com.example.BES.services;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;

@Service
public class MailSenderService {
    @Autowired
    private JavaMailSender mailSender;

    public void sendEmail(String toEmail, String subject, String body){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("bennylim0926@gmail.com");
        message.setTo(toEmail);
        message.setText(body);
        message.setSubject(subject);
        mailSender.send(message);
    }

    public void sendEmailWithAttachment(String toEmail, String subject, String body, byte[] source) throws MessagingException{
        // SimpleMailMessage message = new SimpleMailMessage();
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true);

        DataSource dataSource = new ByteArrayDataSource(source, "image/jpeg");
            // use with MimeBodyPart
        MimeBodyPart attachmentPart = new MimeBodyPart();
        attachmentPart.setDataHandler(new DataHandler(dataSource));
        attachmentPart.setFileName("random.jpg");

        messageHelper.setFrom("bennylim0926@gmail.com");
        messageHelper.setTo(toEmail);
        messageHelper.setText(body);
        messageHelper.setSubject(subject);
        messageHelper.addAttachment("test.png", dataSource);;
        
        mailSender.send(mimeMessage);
    }
}
