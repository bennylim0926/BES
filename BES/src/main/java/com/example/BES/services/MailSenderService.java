package com.example.BES.services;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.GetEmailTemplateDto;
import com.example.BES.enums.Constant;
import com.example.BES.models.EventGenreParticipantId;
import com.example.BES.models.Participant;
import com.google.zxing.WriterException;

import jakarta.activation.DataSource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.util.ByteArrayDataSource;
import org.springframework.core.env.Environment;

@Service
public class MailSenderService {
    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    EmailTemplateService emailTemplateService;

    @Autowired
    QrCodeService qrService;

    @Autowired
    private Environment env;


    public void sendEmailWithAttachment(String eventName, Participant receiver, List<EventGenreParticipantId> ids, String referenceCode, String displayName) throws MessagingException, WriterException, IOException{
        GetEmailTemplateDto template = emailTemplateService.getTemplateByEventName(eventName);
        if (template == null) {
            throw new RuntimeException("No email template found for event: " + eventName);
        }
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper messageHelper = new MimeMessageHelper(mimeMessage, true);
        EventGenreParticipantId first = ids.get(0);
        String registerLink = String.format(
            "%s/api/v1/event/register-participant/%d/%d",
            env.getProperty("DOMAIN"),
            first.getParticipantId(),
            first.getEventId());
        byte[] sourceBytes = qrService.generateQrCode(registerLink, 350, 350);
        DataSource dataSource = new ByteArrayDataSource(sourceBytes, "image/jpeg");
        messageHelper.addAttachment("audition-qr.jpg", dataSource);
            // use with MimeBodyPart
        messageHelper.setFrom(Constant.SENDER_EMAIL.getLabel());
        messageHelper.setTo(receiver.getParticipantEmail());
        String body = template.getBody()
            .replace("{name}", displayName != null ? displayName : receiver.getParticipantName())
            .replace("{refCode}", referenceCode != null ? referenceCode : "");
        messageHelper.setText(body);
        messageHelper.setSubject(template.getSubject());

        mailSender.send(mimeMessage);
    }

    public static String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        // Replace space and dot with underscore
        return key.replaceAll("[ .]", "_");
    }
}
