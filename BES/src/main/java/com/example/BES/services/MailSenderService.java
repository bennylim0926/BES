package com.example.BES.services;

import java.io.IOException;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.BES.dtos.EmailRequestDto;
import com.example.BES.dtos.ParticpantsDto;
import com.example.BES.enums.EmailTemplates;

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

    @Autowired
    EmailTemplates emailTemplates;

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
        messageHelper.addAttachment("qr.png", dataSource);;
        
        mailSender.send(mimeMessage);
    }

    public void bulkSendEmailWithAttachment(EmailRequestDto dto) throws MessagingException{
        EmailTemplates.Template template = emailTemplates.getEvents().get(normalizeKey(dto.eventName)); 
        for(ParticpantsDto participant: dto.newPaidParticipants){
            // update the database record to mark sent as true
            byte[] sourceBytes = fetchRandomImage();
            sendEmailWithAttachment(
                participant.getEmail(),
                template.getSubject(),
                template.getBody().replace("{name}", participant.getName()), 
                sourceBytes);
        }
    }
    
    public byte[] fetchRandomImage() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://picsum.photos/200";
        ResponseEntity<byte[]> response = restTemplate.getForEntity(url, byte[].class);
        return response.getBody(); // this is your random image as byte[]
    }

    public static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        // Replace space and dot with underscore
        return key.replaceAll("[ .]", "_");
    }
}
